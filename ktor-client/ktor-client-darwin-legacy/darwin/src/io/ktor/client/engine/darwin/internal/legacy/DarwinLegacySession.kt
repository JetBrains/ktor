/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import platform.Foundation.*
import kotlin.coroutines.*

@OptIn(UnsafeNumber::class)
internal class DarwinLegacySession(
    private val config: DarwinLegacyClientEngineConfig,
    private val requestQueue: NSOperationQueue?
) : Closeable {
    private val closed = atomic(false)

    private val sessionAndDelegate = config.sessionAndDelegate ?: createSession(config, requestQueue)
    private val session = sessionAndDelegate.first
    private val delegate = sessionAndDelegate.second

    @OptIn(InternalAPI::class)
    internal suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData {
        val nativeRequest = request.toNSUrlRequest()
            .apply(config.requestConfig)
        val task = session.dataTaskWithRequest(nativeRequest)
        val response = delegate.read(request, callContext, task)

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
    config: DarwinLegacyClientEngineConfig,
    requestQueue: NSOperationQueue?
): Pair<NSURLSession, KtorLegacyNSURLSessionDelegate> {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
        setupProxy(config)
        setHTTPCookieStorage(null)

        config.sessionConfig(this)
    }
    val delegate = KtorLegacyNSURLSessionDelegate(config.challengeHandler)

    return NSURLSession.sessionWithConfiguration(
        configuration,
        delegate,
        delegateQueue = requestQueue
    ) to delegate
}
