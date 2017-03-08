package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

@Deprecated("Order of parameters has been changed to unify API and allow of default values", ReplaceWith("TextContent(text, contentType)"))
fun TextContent(contentType: ContentType, text: String) = TextContent(text, contentType)

class TextContent(val text: String, val contentType: ContentType? = null, override val status: HttpStatusCode? = null) : FinalContent.ByteArrayContent() {
    private val bytes by lazy { text.toByteArray(contentType?.charset() ?: Charsets.UTF_8) }

    override val headers by lazy {
        ValuesMap.build(true) {
            if (contentType != null) {
                contentType(contentType)
            }
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes

    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}