package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.*

@OptIn(UnsafeNumber::class)
internal class DarwinSession(
    private val config: DarwinClientEngineConfig,
    requestQueue: NSOperationQueue?
) : Closeable {
    private val closed = atomic(false)

    private val sessionAndDelegate = config.sessionAndDelegate ?: createSession(config, requestQueue)
    private val session = sessionAndDelegate.first
    private val delegate = sessionAndDelegate.second

    @OptIn(InternalAPI::class)
    internal suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData {
        val nativeRequest = request.toNSUrlRequest()
            .apply(config.requestConfig)
        val (task, response) = if (request.isUpgradeRequest()) {
            val task = session.webSocketTaskWithRequest(nativeRequest)
            val response = delegate.read(task, callContext)
            task to response
        } else {
            val task = session.dataTaskWithRequest(nativeRequest)
            val response = delegate.read(request, callContext, task)
            task to response
        }

        callContext.job.invokeOnCompletion { cause ->
            if (cause != null) {
                task.cancel()
            }
        }

        task.resume()

        try {
            return response.await()
        } catch (cause: Throwable) {
            if (task.state == NSURLSessionTaskStateRunning) task.cancel()
            throw cause
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        session.finishTasksAndInvalidate()
    }
}

@OptIn(UnsafeNumber::class)
internal fun createSession(
    config: DarwinClientEngineConfig,
    requestQueue: NSOperationQueue?
): Pair<NSURLSession, KtorNSURLSessionDelegate> {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
        setupProxy(config)
        setHTTPCookieStorage(null)

        config.sessionConfig(this)
    }
    val delegate = KtorNSURLSessionDelegate(config.challengeHandler)

    return NSURLSession.sessionWithConfiguration(
        configuration,
        delegate,
        delegateQueue = requestQueue
    ) to delegate
}
