/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.utils.io.*

/**
 * [OutgoingContent] to respond with [ByteWriteChannel].
 * The stream would be automatically closed after [body] finish.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.ChannelWriterContent)
 */
public class ChannelWriterContent(
    private val body: suspend ByteWriteChannel.() -> Unit,
    override val contentType: ContentType?,
    override val status: HttpStatusCode? = null,
    override val contentLength: Long? = null
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        body(channel)
    }
}
