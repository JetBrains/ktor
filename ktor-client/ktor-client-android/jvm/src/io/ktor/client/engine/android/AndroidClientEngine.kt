/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.*
import javax.net.ssl.*
import kotlin.coroutines.*

private val METHODS_WITHOUT_BODY = listOf(HttpMethod.Get, HttpMethod.Head)

/**
 * An Android client engine.
 */
@OptIn(InternalAPI::class)
public class AndroidClientEngine(override val config: AndroidEngineConfig) : HttpClientEngineBase("ktor-android") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability, SSECapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val requestTime = GMTDate()

        val url: String = data.url.toString()
        val outgoingContent: OutgoingContent = data.body
        val contentLength: Long? = data.headers[HttpHeaders.ContentLength]?.toLong()
            ?: outgoingContent.contentLength

        val connection: HttpURLConnection = getProxyAwareConnection(url).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout

            setupTimeoutAttributes(data)

            if (this is HttpsURLConnection) {
                config.sslManager(this)
            }

            requestMethod = data.method.value
            useCaches = false
            instanceFollowRedirects = false

            mergeHeaders(data.headers, outgoingContent) { key: String, value: String ->
                addRequestProperty(key, value)
            }

            config.requestConfig(this)

            if (data.method in METHODS_WITHOUT_BODY) {
                if (outgoingContent.isEmpty()) {
                    return@apply
                }

                error("Request of type ${data.method} couldn't send a body with the [Android] engine.")
            }

            if (contentLength == null && getRequestProperty(HttpHeaders.TransferEncoding) == null) {
                addRequestProperty(HttpHeaders.TransferEncoding, "chunked")
            }

            contentLength?.let { setFixedLengthStreamingMode(it) } ?: setChunkedStreamingMode(0)
            doOutput = true

            outgoingContent.writeTo(outputStream, callContext)
        }

        return connection.timeoutAwareConnection(data) { current ->
            val responseCode = current.responseCode
            val responseMessage = current.responseMessage
            val statusCode = responseMessage?.let { HttpStatusCode(responseCode, it) }
                ?: HttpStatusCode.fromValue(responseCode)

            val content: ByteReadChannel = current.content(responseCode, callContext)
            val headerFields: Map<String, List<String>> = current.headerFields
                .mapKeys { it.key?.lowercase(Locale.getDefault()) ?: "" }
                .filter { it.key.isNotBlank() }

            val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
            val responseHeaders = HeadersImpl(headerFields)

            val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, statusCode, responseHeaders, content, outgoingContent, callContext)
                ?: content

            HttpResponseData(statusCode, requestTime, responseHeaders, version, responseBody, callContext)
        }
    }

    private fun getProxyAwareConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection: URLConnection = config.proxy?.let { url.openConnection(it) } ?: url.openConnection()
        return connection as HttpURLConnection
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext", "DEPRECATION")
internal suspend fun OutgoingContent.writeTo(
    stream: OutputStream,
    callContext: CoroutineContext
): Unit = stream.use { blockingOutput ->
    when (this) {
        is OutgoingContent.ByteArrayContent -> blockingOutput.write(bytes())
        is OutgoingContent.ReadChannelContent -> run {
            readFrom().copyTo(blockingOutput)
        }

        is OutgoingContent.WriteChannelContent -> {
            val channel = GlobalScope.writer(callContext) {
                writeTo(channel)
            }.channel

            channel.copyTo(blockingOutput)
        }

        is OutgoingContent.NoContent -> {
        }

        is OutgoingContent.ContentWrapper -> delegate().writeTo(stream, callContext)

        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}
