/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*

/**
 * Represents a content that is produced by [body] function
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.WriterContent)
 */
public class WriterContent(
    private val body: suspend Writer.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null,
    override val contentLength: Long? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val charset = contentType.charset() ?: Charsets.UTF_8
        withBlocking {
            channel.writer(charset).use {
                it.body()
            }
        }
    }
}
