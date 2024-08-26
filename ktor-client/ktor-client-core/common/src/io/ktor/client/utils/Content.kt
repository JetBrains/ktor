/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Concrete [OutgoingContent] without a payload.
 */
public data object EmptyContent : OutgoingContent.NoContent() {
    override val contentLength: Long = 0
}

/**
 * Generates a new [OutgoingContent] of the same abstract type
 * but with [OutgoingContent.headers] transformed by the specified [block].
 */
public fun OutgoingContent.wrapHeaders(block: (Headers) -> Headers): OutgoingContent = when (this) {
    is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)
    }
    is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun readFrom(): ByteReadChannel = this@wrapHeaders.readFrom()

        override fun readFrom(range: LongRange): ByteReadChannel = this@wrapHeaders.readFrom(range)
    }
    is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun writeTo(channel: ByteWriteChannel) = this@wrapHeaders.writeTo(channel)
    }
    is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun bytes(): ByteArray = this@wrapHeaders.bytes()
    }
    is OutgoingContent.ProtocolUpgrade -> object : OutgoingContent.ProtocolUpgrade() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType

        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun upgrade(
            input: ByteReadChannel,
            output: ByteWriteChannel,
            engineContext: CoroutineContext,
            userContext: CoroutineContext
        ): Job = this@wrapHeaders.upgrade(input, output, engineContext, userContext)
    }
    is OutgoingContent.ContentWrapper -> delegate().wrapHeaders(block)
}
