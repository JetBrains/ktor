/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.curl.internal

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import libcurl.*
import kotlin.coroutines.*

@OptIn(ExperimentalForeignApi::class)
internal suspend fun HttpRequestData.toCurlRequest(config: CurlClientEngineConfig): CurlRequestData = CurlRequestData(
    url = url.toString(),
    method = method.value,
    headers = headersToCurl(),
    proxy = config.proxy,
    content = body.toByteChannel(),
    contentLength = body.contentLength ?: headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
    connectTimeout = getCapabilityOrNull(HttpTimeoutCapability)?.connectTimeoutMillis,
    executionContext = executionContext,
    forceProxyTunneling = config.forceProxyTunneling,
    sslVerify = config.sslVerify,
    caInfo = config.caInfo,
    caPath = config.caPath
)

internal class CurlRequestData @OptIn(ExperimentalForeignApi::class) constructor(
    val url: String,
    val method: String,
    val headers: CPointer<curl_slist>,
    val proxy: ProxyConfig?,
    val content: ByteReadChannel,
    val contentLength: Long,
    val connectTimeout: Long?,
    val executionContext: Job,
    val forceProxyTunneling: Boolean,
    val sslVerify: Boolean,
    val caInfo: String?,
    val caPath: String?
) {
    override fun toString(): String =
        "CurlRequestData(url='$url', method='$method', content: $contentLength bytes)"
}

internal class CurlResponseBuilder(val request: CurlRequestData) {
    val headersBytes = BytePacketBuilder()
    val bodyChannel = ByteChannel(true).apply {
        attachJob(request.executionContext)
    }
}

internal sealed class CurlResponseData

internal class CurlSuccess(
    val status: Int,
    val version: UInt,
    val headersBytes: ByteArray,
    val bodyChannel: ByteReadChannel
) : CurlResponseData() {
    override fun toString(): String = "CurlSuccess(${HttpStatusCode.fromValue(status)})"
}

internal class CurlFail(
    val cause: Throwable
) : CurlResponseData() {
    override fun toString(): String = "CurlFail($cause)"
}

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun OutgoingContent.toByteChannel(): ByteReadChannel = when (this@toByteChannel) {
    is OutgoingContent.ByteArrayContent -> {
        val bytes = bytes()
        ByteReadChannel(bytes, 0, bytes.size)
    }

    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(coroutineContext) {
        writeTo(channel)
    }.channel

    is OutgoingContent.ReadChannelContent -> readFrom()
    is OutgoingContent.NoContent -> ByteReadChannel.Empty
    is OutgoingContent.ContentWrapper -> delegate().toByteChannel()
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this@toByteChannel)
}
