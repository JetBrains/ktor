/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal val CALL_COROUTINE = CoroutineName("call-context")
internal val CLIENT_CONFIG = AttributeKey<HttpClientConfig<*>>("client-config")

/**
 * Serves as the base interface for an [HttpClient]'s engine.
 */
public interface HttpClientEngine : CoroutineScope, Closeable {
    /**
     * Specifies [CoroutineDispatcher] for I/O operations.
     */
    public val dispatcher: CoroutineDispatcher

    /**
     * Provides access to an engine's configuration.
     */
    public val config: HttpClientEngineConfig

    /**
     * Set of supported engine extensions.
     */
    public val supportedCapabilities: Set<HttpClientEngineCapability<*>>
        get() = emptySet()

    private val closed: Boolean
        get() = !(coroutineContext[Job]?.isActive ?: false)

    /**
     * Creates a new [HttpClientCall] specific for this engine, using a request [data].
     */
    @InternalAPI
    public suspend fun execute(data: HttpRequestData): HttpResponseData

    /**
     * Installs the engine to [HttpClient].
     */
    @InternalAPI
    public fun install(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Engine) { content ->
            val builder = HttpRequestBuilder().apply {
                takeFromWithExecutionContext(context)
                setBody(content)
            }

            client.monitor.raise(HttpRequestIsReadyForSending, builder)

            val requestData = builder.build().apply {
                attributes.put(CLIENT_CONFIG, client.config)
            }

            validateHeaders(requestData)
            checkExtensions(requestData)

            val responseData = executeWithinCallContext(requestData)
            val call = HttpClientCall(client, requestData, responseData)

            val response = call.response
            client.monitor.raise(HttpResponseReceived, response)

            response.coroutineContext.job.invokeOnCompletion {
                if (it != null) {
                    client.monitor.raise(HttpResponseCancelled, response)
                }
            }

            proceedWith(call)
        }
    }

    /**
     * Creates a call context and uses it as a coroutine context to [execute] a request.
     */
    @OptIn(InternalAPI::class)
    private suspend fun executeWithinCallContext(requestData: HttpRequestData): HttpResponseData {
        val callContext = createCallContext(requestData.executionContext)

        val context = callContext + KtorCallContextElement(callContext)
        return async(context) {
            if (closed) {
                throw ClientEngineClosedException()
            }

            execute(requestData)
        }.await()
    }

    private fun checkExtensions(requestData: HttpRequestData) {
        for (requestedExtension in requestData.requiredCapabilities) {
            require(supportedCapabilities.contains(requestedExtension)) { "Engine doesn't support $requestedExtension" }
        }
    }
}

/**
 * A factory of [HttpClientEngine] with a specific [T] of [HttpClientEngineConfig].
 */
public interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    /**
     * Creates a new [HttpClientEngine] optionally specifying a [block] configuring [T].
     */
    public fun create(block: T.() -> Unit = {}): HttpClientEngine
}

/**
 * Creates a new [HttpClientEngineFactory] based on this one
 * with further configurations from the [nested] block.
 */
public fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(
    nested: T.() -> Unit
): HttpClientEngineFactory<T> {
    val parent = this

    return object : HttpClientEngineFactory<T> {
        override fun create(block: T.() -> Unit): HttpClientEngine = parent.create {
            nested()
            block()
        }
    }
}

/**
 * Creates a call context with the specified [parentJob] to be used during call execution in the engine. Call context
 * inherits [coroutineContext], but overrides job and coroutine name so that call job's parent is [parentJob] and
 * call coroutine's name is "call-context".
 */
internal suspend fun HttpClientEngine.createCallContext(parentJob: Job): CoroutineContext {
    val callJob = Job(parentJob)
    val callContext = coroutineContext + callJob + CALL_COROUTINE

    attachToUserJob(callJob)

    return callContext
}

/**
 * Validates request headers and fails if there are unsafe headers supplied
 */
private fun validateHeaders(request: HttpRequestData) {
    val requestHeaders = request.headers
    val unsafeRequestHeaders = requestHeaders.names().filter {
        it in HttpHeaders.UnsafeHeadersList
    }
    if (unsafeRequestHeaders.isNotEmpty()) {
        throw UnsafeHeaderException(unsafeRequestHeaders.toString())
    }
}
