/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Returns [OutgoingContent] compressed with [contentEncoder] if possible.
 */
public fun OutgoingContent.compressed(
    contentEncoder: ContentEncoder,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
): OutgoingContent? = when (this) {
    is OutgoingContent.ReadChannelContent ->
        CompressedReadChannelResponse(this, { readFrom() }, contentEncoder, coroutineContext)

    is OutgoingContent.WriteChannelContent ->
        CompressedWriteChannelResponse(this, contentEncoder, coroutineContext)

    is OutgoingContent.ByteArrayContent ->
        CompressedReadChannelResponse(this, { ByteReadChannel(bytes()) }, contentEncoder, coroutineContext)

    is OutgoingContent.NoContent -> null
    is OutgoingContent.ProtocolUpgrade -> null
    is OutgoingContent.ContentWrapper -> delegate().compressed(contentEncoder, coroutineContext)
}

private class CompressedReadChannelResponse(
    val original: OutgoingContent,
    val delegateChannel: () -> ByteReadChannel,
    val encoder: ContentEncoder,
    val coroutineContext: CoroutineContext
) : OutgoingContent.ReadChannelContent() {
    override fun readFrom() = encoder.encode(delegateChannel(), coroutineContext)

    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoder.name)
        }
    }

    override val contentType: ContentType? get() = original.contentType
    override val status: HttpStatusCode? get() = original.status
    override val contentLength: Long?
        get() = original.contentLength?.let { encoder.predictCompressedLength(it) }?.takeIf { it >= 0 }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

private class CompressedWriteChannelResponse(
    val original: WriteChannelContent,
    val encoder: ContentEncoder,
    val coroutineContext: CoroutineContext
) : OutgoingContent.WriteChannelContent() {
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoder.name)
        }
    }

    override val contentType: ContentType? get() = original.contentType
    override val status: HttpStatusCode? get() = original.status
    override val contentLength: Long?
        get() = original.contentLength?.let { encoder.predictCompressedLength(it) }?.takeIf { it >= 0 }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(coroutineContext) {
            encoder.encode(channel, coroutineContext).use {
                original.writeTo(this)
            }
        }
    }
}
