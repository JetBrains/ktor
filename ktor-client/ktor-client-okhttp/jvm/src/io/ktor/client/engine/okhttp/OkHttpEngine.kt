/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.okhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod
import okio.*
import java.util.concurrent.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
public class OkHttpEngine(override val config: OkHttpConfig) : HttpClientEngineBase("ktor-okhttp") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    private val requestsJob: CoroutineContext

    override val coroutineContext: CoroutineContext

    /**
     * Cache that keeps least recently used [OkHttpClient] instances.
     */
    private val clientCache = createLRUCache(::createOkHttpClient, {}, config.clientCacheSize)

    init {
        val parent = super.coroutineContext[Job]!!
        requestsJob = SilentSupervisor(parent)
        coroutineContext = super.coroutineContext + requestsJob

        @OptIn(ExperimentalCoroutinesApi::class)
        GlobalScope.launch(super.coroutineContext, start = CoroutineStart.ATOMIC) {
            try {
                requestsJob[Job]!!.join()
            } finally {
                clientCache.forEach { (_, client) ->
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
        }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val engineRequest = data.convertToOkHttpRequest(callContext)

        val requestEngine = clientCache[data.getCapabilityOrNull(HttpTimeoutCapability)]
            ?: error("OkHttpClient can't be constructed because HttpTimeout plugin is not installed")

        return when {
            data.isUpgradeRequest() -> executeWebSocketRequest(requestEngine, engineRequest, callContext)
            data.isSseRequest() -> executeServerSendEventsRequest(requestEngine, engineRequest, callContext)
            else -> executeHttpRequest(requestEngine, engineRequest, callContext, data)
        }
    }

    override fun close() {
        super.close()
        (requestsJob[Job] as CompletableJob).complete()
    }

    private suspend fun executeWebSocketRequest(
        engine: OkHttpClient,
        engineRequest: Request,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()
        val session = OkHttpWebsocketSession(
            engine,
            config.webSocketFactory ?: engine,
            engineRequest,
            callContext
        ).apply { start() }

        val originResponse = session.originResponse.await()
        return buildResponseData(originResponse, requestTime, session, callContext)
    }

    private suspend fun executeServerSendEventsRequest(
        engine: OkHttpClient,
        engineRequest: Request,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()
        val session = OkHttpSSESession(
            engine,
            engineRequest,
            callContext
        )

        val originResponse = session.originResponse.await()
        return buildResponseData(originResponse, requestTime, session, callContext)
    }

    private suspend fun executeHttpRequest(
        engine: OkHttpClient,
        engineRequest: Request,
        callContext: CoroutineContext,
        requestData: HttpRequestData
    ): HttpResponseData {
        val requestTime = GMTDate()
        val response = engine.execute(engineRequest, requestData, callContext)

        val body = response.body
        callContext[Job]!!.invokeOnCompletion { body?.close() }

        val responseContent = body?.source()?.toChannel(callContext, requestData) ?: ByteReadChannel.Empty
        return buildResponseData(response, requestTime, responseContent, callContext)
    }

    private fun buildResponseData(
        response: Response,
        requestTime: GMTDate,
        body: Any,
        callContext: CoroutineContext
    ): HttpResponseData {
        val status = HttpStatusCode(response.code, response.message)
        val version = response.protocol.fromOkHttp()
        val headers = response.headers.fromOkHttp()

        return HttpResponseData(status, requestTime, headers, version, body, callContext)
    }

    private companion object {
        /**
         * It's an artificial prototype object to be used to create actual clients and eliminate the following issue:
         * https://github.com/square/okhttp/issues/3372.
         */
        val okHttpClientPrototype: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }
    }

    private fun createOkHttpClient(timeoutExtension: HttpTimeoutConfig?): OkHttpClient {
        val builder = (config.preconfigured ?: okHttpClientPrototype).newBuilder()

        builder.dispatcher(Dispatcher())
        builder.apply(config.config)
        config.proxy?.let { builder.proxy(it) }
        timeoutExtension?.let {
            builder.setupTimeoutAttributes(it)
        }

        return builder.build()
    }
}

@OptIn(DelicateCoroutinesApi::class, InternalCoroutinesApi::class)
private fun BufferedSource.toChannel(context: CoroutineContext, requestData: HttpRequestData): ByteReadChannel =
    GlobalScope.writer(context) {
        use { source ->
            var lastRead = 0
            while (source.isOpen && context.isActive && lastRead >= 0) {
                channel.write { buffer ->
                    lastRead = try {
                        source.read(buffer)
                    } catch (cause: Throwable) {
                        val cancelOrCloseCause =
                            kotlin.runCatching { context.job.getCancellationException() }.getOrNull() ?: cause
                        throw mapExceptions(cancelOrCloseCause, requestData)
                    }
                }
                channel.flush()
            }
        }
    }.channel

private fun mapExceptions(cause: Throwable, request: HttpRequestData): Throwable = when (cause) {
    is java.net.SocketTimeoutException -> SocketTimeoutException(request, cause)
    else -> cause
}

@OptIn(InternalAPI::class)
private fun HttpRequestData.convertToOkHttpRequest(callContext: CoroutineContext): Request {
    val builder = Request.Builder()

    with(builder) {
        url(url.toString())

        mergeHeaders(headers, body) { key, value ->
            if (key == HttpHeaders.ContentLength) return@mergeHeaders

            addHeader(key, value)
        }

        val bodyBytes = if (HttpMethod.permitsRequestBody(method.value)) {
            body.convertToOkHttpBody(callContext)
        } else {
            null
        }

        method(method.value, bodyBytes)
    }

    return builder.build()
}

@OptIn(DelicateCoroutinesApi::class)
internal fun OutgoingContent.convertToOkHttpBody(callContext: CoroutineContext): RequestBody = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().let {
        it.toRequestBody(contentType.toString().toMediaTypeOrNull(), 0, it.size)
    }

    is OutgoingContent.ReadChannelContent -> StreamRequestBody(contentLength) { readFrom() }
    is OutgoingContent.WriteChannelContent -> {
        StreamRequestBody(contentLength) { GlobalScope.writer(callContext) { writeTo(channel) }.channel }
    }

    is OutgoingContent.NoContent -> ByteArray(0).toRequestBody(null, 0, 0)
    is OutgoingContent.ContentWrapper -> delegate().convertToOkHttpBody(callContext)
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
}

/**
 * Update [OkHttpClient.Builder] setting timeout configuration taken from
 * [HttpTimeout.HttpTimeoutCapabilityConfiguration].
 */
@OptIn(InternalAPI::class)
private fun OkHttpClient.Builder.setupTimeoutAttributes(
    timeoutAttributes: HttpTimeoutConfig
): OkHttpClient.Builder {
    timeoutAttributes.connectTimeoutMillis?.let {
        connectTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
    }
    timeoutAttributes.socketTimeoutMillis?.let {
        readTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
        writeTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
    }
    return this
}
