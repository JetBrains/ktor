/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal expect class TestHttpClientEngineBridge(engine: TestHttpClientEngine, app: TestApplicationEngine) {
    val supportedCapabilities: Set<HttpClientEngineCapability<*>>

    suspend fun runWebSocketRequest(
        url: String,
        headers: Headers,
        content: OutgoingContent,
        callContext: CoroutineContext
    ): Pair<TestApplicationCall, WebSocketSession>
}

public class TestHttpClientEngine(override val config: TestHttpClientConfig) : HttpClientEngineBase("ktor-test") {
    private val app: TestApplicationEngine = config.app

    private val bridge = TestHttpClientEngineBridge(this, app)

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = bridge.supportedCapabilities

    private val clientJob: CompletableJob = Job(app.coroutineContext[Job])

    override val coroutineContext: CoroutineContext = clientJob + dispatcher + CoroutineExceptionHandler { _, _ -> }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        try {
            if (data.isUpgradeRequest()) {
                val (testServerCall, session) = with(data) {
                    bridge.runWebSocketRequest(url.fullPath, headers, body, callContext)
                }
                return with(testServerCall.response) {
                    httpResponseData(session)
                }
            }

            val testServerCall = with(data) {
                runRequest(method, url, headers, body, url.protocol, data.getCapabilityOrNull(HttpTimeoutCapability))
            }
            val response = testServerCall.response
            val status = response.statusOrNotFound()
            val headers = response.headers.allValues().takeUnless { it.isEmpty() } ?: Headers
                .build { append(HttpHeaders.ContentLength, "0") }
            val body = ByteReadChannel(response.byteContent ?: byteArrayOf())

            val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, status, headers, body, data.body, callContext)
                ?: body

            return HttpResponseData(
                status,
                GMTDate(),
                headers,
                HttpProtocolVersion.HTTP_1_1,
                responseBody,
                callContext
            )
        } catch (cause: Throwable) {
            throw cause.mapToKtor(data)
        }
    }

    private suspend fun runRequest(
        method: HttpMethod,
        url: Url,
        headers: Headers,
        content: OutgoingContent,
        protocol: URLProtocol,
        timeoutAttributes: HttpTimeoutConfig? = null
    ): TestApplicationCall {
        return app.handleRequestNonBlocking(timeoutAttributes = timeoutAttributes) {
            this.uri = url.fullPath
            this.port = url.port
            this.method = method
            appendRequestHeaders(headers, content)
            this.protocol = protocol.name

            if (content !is OutgoingContent.NoContent) {
                bodyChannel = content.toByteReadChannel(timeoutAttributes)
            }
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun TestApplicationResponse.httpResponseData(body: Any) = HttpResponseData(
        statusOrNotFound(),
        GMTDate(),
        headers.allValues().takeUnless { it.isEmpty() } ?: Headers
            .build { append(HttpHeaders.ContentLength, "0") },
        HttpProtocolVersion.HTTP_1_1,
        body,
        callContext()
    )

    @OptIn(InternalAPI::class)
    internal fun TestApplicationRequest.appendRequestHeaders(
        headers: Headers,
        content: OutgoingContent
    ) {
        mergeHeaders(headers, content) { name, value ->
            addHeader(name, value)
        }
    }

    override fun close() {
        clientJob.complete()
    }

    public companion object : HttpClientEngineFactory<TestHttpClientConfig> {
        override fun create(block: TestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = TestHttpClientConfig().apply(block)
            return TestHttpClientEngine(config)
        }
    }

    private fun OutgoingContent.toByteReadChannel(timeoutAttributes: HttpTimeoutConfig?): ByteReadChannel =
        when (this) {
            is OutgoingContent.NoContent -> ByteReadChannel.Empty
            is OutgoingContent.ByteArrayContent -> ByteReadChannel(bytes())
            is OutgoingContent.ReadChannelContent -> readFrom()
            is OutgoingContent.WriteChannelContent -> writer(coroutineContext) {
                val counted = channel.counted()
                val job = launch {
                    writeTo(counted)
                }

                configureSocketTimeoutIfNeeded(timeoutAttributes, job) { counted.totalBytesWritten }
            }.channel

            is OutgoingContent.ContentWrapper -> delegate().toByteReadChannel(timeoutAttributes)
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
        }

    private fun TestApplicationResponse.statusOrNotFound() = status() ?: HttpStatusCode.NotFound
}
