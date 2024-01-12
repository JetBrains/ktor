/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.apache.http.HttpHeaders
import org.apache.http.HttpRequest
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.entity.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.nio.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
internal class ApacheRequestProducer(
    val requestData: HttpRequestData,
    private val config: ApacheEngineConfig,
    callContext: CoroutineContext
) : HttpAsyncRequestProducer, CoroutineScope {

    private val request: HttpUriRequest = setupRequest()

    private val host = URIUtils.extractHost(request.uri)
        ?: throw IllegalArgumentException("Cannot extract host from URL ${request.uri}")

    private val interestController = InterestControllerHolder()

    private val producerJob = Job()
    override val coroutineContext: CoroutineContext = callContext + producerJob

    @Suppress("DEPRECATION")
    @OptIn(DelicateCoroutinesApi::class)
    private val channel: ByteReadChannel = when (val body = requestData.body) {
        is OutgoingContent.ByteArrayContent -> ByteReadChannel(body.bytes())
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(body)
        is OutgoingContent.NoContent -> ByteReadChannel.Empty
        is OutgoingContent.ReadChannelContent -> body.readFrom()
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(callContext, autoFlush = true) {
            body.writeTo(channel)
        }.channel
    }

    init {
        producerJob.invokeOnCompletion { cause ->
            channel.cancel(cause)
        }
    }

    override fun isRepeatable(): Boolean = true

    override fun getTarget(): HttpHost = host

    override fun generateRequest(): HttpRequest = request

    override fun requestCompleted(context: HttpContext) {
    }

    override fun resetRequest() {}

    override fun failed(cause: Exception) {
        val mappedCause = mapCause(cause, requestData)
        channel.cancel(mappedCause)
        producerJob.completeExceptionally(mappedCause)
    }

    override fun produceContent(encoder: ContentEncoder, ioctrl: IOControl) {
        if (interestController.outputSuspended) {
            return
        }

        var result: Int
        do {
            result = channel.readAvailable { buffer: ByteBuffer ->
                encoder.write(buffer)
            }
        } while (result > 0)

        if (channel.isClosedForRead) {
            channel.closedCause?.let { throw it }
            encoder.complete()
            return
        }

        if (result == -1) {
            interestController.suspendOutput(ioctrl)
            launch(Dispatchers.Unconfined) {
                try {
                    channel.awaitContent()
                } finally {
                    interestController.resumeOutputIfPossible()
                }
            }
        }
    }

    override fun close() {
        channel.cancel()
        producerJob.complete()
    }

    private fun setupRequest(): HttpUriRequest = with(requestData) {
        val builder = RequestBuilder.create(method.value)!!
        builder.uri = url.toURI()

        val content = requestData.body
        var length: String? = null
        var type: String? = null

        mergeHeaders(headers, content) { key, value ->
            when (key) {
                HttpHeaders.CONTENT_LENGTH -> length = value
                HttpHeaders.CONTENT_TYPE -> type = value
                else -> builder.addHeader(key, value)
            }
        }

        if ((method != HttpMethod.Get && method != HttpMethod.Head) || body !is OutgoingContent.NoContent) {
            builder.entity = BasicHttpEntity().apply {
                val lengthResult = length
                if (lengthResult.isNullOrBlank()) {
                    isChunked = true
                } else {
                    contentLength = lengthResult.toLong()
                }

                setContentType(type)
            }
        }

        with(config) {
            builder.config = RequestConfig.custom()
                .setRedirectsEnabled(followRedirects)
                .setSocketTimeout(socketTimeout)
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .customRequest()
                .setupTimeoutAttributes(requestData)
                .build()
        }

        return builder.build()
    }
}

@OptIn(InternalAPI::class)
private fun RequestConfig.Builder.setupTimeoutAttributes(requestData: HttpRequestData): RequestConfig.Builder = also {
    requestData.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttributes ->
        timeoutAttributes.connectTimeoutMillis?.let { setConnectTimeout(convertLongTimeoutToIntWithInfiniteAsZero(it)) }
        timeoutAttributes.socketTimeoutMillis?.let { setSocketTimeout(convertLongTimeoutToIntWithInfiniteAsZero(it)) }
    }
}
