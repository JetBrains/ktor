/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.apache.hc.client5.http.async.methods.*
import org.apache.hc.client5.http.config.*
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.nio.*
import org.apache.hc.core5.http.nio.support.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
internal fun ApacheRequestProducer(
    requestData: HttpRequestData,
    config: Apache5EngineConfig,
    callContext: CoroutineContext
): AsyncRequestProducer {
    val content = requestData.body
    var length: String? = null
    var type: String? = null

    mergeHeaders(requestData.headers, content) { key, value ->
        when (key) {
            HttpHeaders.ContentLength -> length = value
            HttpHeaders.ContentType -> type = value
        }
    }

    val isGetOrHead = requestData.method == HttpMethod.Get || requestData.method == HttpMethod.Head
    val hasContent = requestData.body !is OutgoingContent.NoContent
    val contentLength = length?.toLong() ?: -1
    val isChunked = contentLength == -1L && !isGetOrHead && hasContent

    return BasicRequestProducer(
        setupRequest(requestData, config),
        if (!hasContent && isGetOrHead) {
            null
        } else {
            ApacheRequestEntityProducer(requestData, callContext, contentLength, type, isChunked)
        }
    )
}

@OptIn(InternalAPI::class)
private fun setupRequest(requestData: HttpRequestData, config: Apache5EngineConfig): HttpRequest = with(requestData) {
    val request = ConfigurableHttpRequest(method.value, url.toURI())

    mergeHeaders(headers, body) { key, value ->
        when (key) {
            HttpHeaders.ContentLength -> {}
            HttpHeaders.ContentType -> {}
            else -> request.addHeader(key, value)
        }
    }

    with(config) {
        request.config = RequestConfig.custom()
            .setRedirectsEnabled(followRedirects)
            .setConnectionRequestTimeout(connectionRequestTimeout, TimeUnit.MILLISECONDS)
            .customRequest()
            .build()
    }

    return request
}

internal class ApacheRequestEntityProducer(
    private val requestData: HttpRequestData,
    callContext: CoroutineContext,
    private val contentLength: Long,
    private val contentType: String?,
    private val isChunked: Boolean
) : AsyncEntityProducer, CoroutineScope {

    private val waitingForContent = atomic(false)
    private val producerJob = Job()
    override val coroutineContext: CoroutineContext = callContext + producerJob

    private val channel: ByteReadChannel = getChannel(callContext, requestData.body)

    @OptIn(DelicateCoroutinesApi::class)
    private fun getChannel(callContext: CoroutineContext, body: OutgoingContent): ByteReadChannel = when (body) {
        is OutgoingContent.ByteArrayContent -> ByteReadChannel(body.bytes())
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(body)
        is OutgoingContent.NoContent -> ByteReadChannel.Empty
        is OutgoingContent.ReadChannelContent -> body.readFrom()
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(callContext, autoFlush = true) {
            body.writeTo(channel)
        }.channel
        is OutgoingContent.ContentWrapper -> getChannel(callContext, body.delegate())
    }

    init {
        producerJob.invokeOnCompletion { cause ->
            channel.cancel(cause)
        }
    }

    override fun releaseResources() {
        channel.cancel()
        producerJob.complete()
    }

    override fun available(): Int = channel.availableForRead

    override fun produce(channel: DataStreamChannel) {
        var result: Int
        do {
            result = this.channel.readAvailable { buffer: ByteBuffer ->
                channel.write(buffer)
            }
        } while (result > 0)

        if (this.channel.isClosedForRead) {
            this.channel.closedCause?.let { throw it }
            channel.endStream()
            return
        }

        if (result == -1 && !waitingForContent.getAndSet(true)) {
            launch(Dispatchers.Unconfined) {
                try {
                    this@ApacheRequestEntityProducer.channel.awaitContent()
                } finally {
                    waitingForContent.value = false
                    channel.requestOutput()
                }
            }
        }
    }

    override fun getContentLength(): Long = contentLength

    override fun getContentType(): String? = contentType

    override fun getContentEncoding(): String? = null

    override fun isChunked(): Boolean = isChunked

    override fun getTrailerNames(): Set<String> = emptySet()

    override fun isRepeatable(): Boolean = false

    override fun failed(cause: Exception) {
        val mappedCause = mapCause(cause, requestData)
        channel.cancel(mappedCause)
        producerJob.completeExceptionally(mappedCause)
    }
}
