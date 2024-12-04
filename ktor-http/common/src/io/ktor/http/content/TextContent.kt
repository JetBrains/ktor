/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

/**
 * Represents a text content that could be sent
 * @property text to be sent
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.TextContent)
 */
public class TextContent(
    public val text: String,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null
) : OutgoingContent.ByteArrayContent() {
    private val bytes = text.toByteArray(contentType.charset() ?: Charsets.UTF_8)

    override val contentLength: Long
        get() = bytes.size.toLong()

    override fun bytes(): ByteArray = bytes

    override fun toString(): String = "TextContent[$contentType] \"${text.take(30)}\""
}
