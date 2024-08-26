/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.html

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.html.*
import kotlinx.html.stream.*

/**
 * Responds to a client with an HTML response using the specified [block] to build an HTML page.
 * You can learn more from [HTML DSL](https://ktor.io/docs/html-dsl.html).
 */
public suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    val text = buildString {
        append("<!DOCTYPE html>\n")
        appendHTML().html(block = block)
    }
    respond(TextContent(text, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}

/**
 * Represents an [OutgoingContent] build using `kotlinx.html`.
 * @see [respondHtml]
 */
@Deprecated("This will be removed from public API", level = DeprecationLevel.ERROR)
public class HtmlContent(
    override val status: HttpStatusCode? = null,
    private val builder: HTML.() -> Unit
) : OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val content = buildPacket {
            try {
                append("<!DOCTYPE html>\n")
                val html = buildString {
                    appendHTML().html(block = builder)
                }
                append(html)
            } catch (cause: Throwable) {
                channel.close(cause)
                throw cause
            }
        }

        channel.writePacket(content)
    }
}
