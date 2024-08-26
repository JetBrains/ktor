/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.jvm.*

/**
 * Sends a [message] as a response.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend inline fun <reified T : Any> ApplicationCall.respond(message: T) {
    // KT-42913
    respond(message, runCatching { typeInfo<T>() }.getOrNull())
}

/**
 * Sends a [message] as a response.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend inline fun <reified T> ApplicationCall.respondNullable(message: T) {
    // KT-42913
    respond(message, runCatching { typeInfo<T>() }.getOrNull())
}

/**
 * Sends a [message] as a response with the specified [status] code.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> ApplicationCall.respond(status: HttpStatusCode, message: T) {
    response.status(status)
    respond(message)
}

/**
 * Sends a [message] of type [messageType] as a response with the specified [status] code.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend fun ApplicationCall.respond(
    status: HttpStatusCode,
    message: Any?,
    messageType: TypeInfo
) {
    response.status(status)
    respond(message, messageType)
}

/**
 * Sends a [message] as a response with the specified [status] code.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend inline fun <reified T> ApplicationCall.respondNullable(status: HttpStatusCode, message: T) {
    response.status(status)
    respondNullable(message)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend fun ApplicationCall.respondRedirect(url: Url, permanent: Boolean = false) {
    respondRedirect(url.toString(), permanent)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * Unlike the other [respondRedirect], it provides a way to build a URL based on current call using the [block] function.
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public suspend inline fun ApplicationCall.respondRedirect(permanent: Boolean = false, block: URLBuilder.() -> Unit) {
    respondRedirect(url(block), permanent)
}

/**
 * Responds to a client with a plain [text] response.
 * @see [io.ktor.server.response.ApplicationResponse]
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondText(
    text: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
) {
    val message = TextContent(text, defaultTextContentType(contentType), status).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a plain text response, using the specified [provider] to build a text.
 * @see [io.ktor.server.response.ApplicationResponse]
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondText(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> String
) {
    val message = TextContent(provider(), defaultTextContentType(contentType), status)
    respond(message)
}

/**
 * Responds to a client with a raw bytes response, using the specified [provider] to build a byte array.
 * @see [io.ktor.server.response.ApplicationResponse]
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondBytes(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> ByteArray
) {
    respond(ByteArrayContent(provider(), contentType, status))
}

/**
 * Responds to a client with a raw bytes response, using specified [bytes].
 * @see [io.ktor.server.response.ApplicationResponse]
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondBytes(
    bytes: ByteArray,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
) {
    respond(ByteArrayContent(bytes, contentType, status).apply(configure))
}

/**
 * Respond with a binary content producer.
 *
 * The [producer] parameter will be called later when an engine is ready to produce content. You don't need to close it.
 * The provided [ByteWriteChannel] will be closed automatically.
 */
public suspend fun ApplicationCall.respondBytesWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    contentLength: Long? = null,
    producer: suspend ByteWriteChannel.() -> Unit
) {
    respond(ChannelWriterContent(producer, contentType ?: ContentType.Application.OctetStream, status, contentLength))
}

/**
 * Creates a default [ContentType] based on the given [contentType] and current call.
 *
 * If [contentType] is `null`, it tries to fetch an already set "Content-Type" response header.
 * If the header is not available, `text/plain` is used. If [contentType] is specified, it uses it.
 *
 * Additionally, if a content type is `Text` and a charset is not set for a content type,
 * it appends `; charset=UTF-8` to the content type.
 */
public fun ApplicationCall.defaultTextContentType(contentType: ContentType?): ContentType {
    val result = when (contentType) {
        null -> {
            val headersContentType = response.headers[HttpHeaders.ContentType]
            headersContentType?.let {
                try {
                    ContentType.parse(headersContentType)
                } catch (_: BadContentTypeFormatException) {
                    null
                }
            } ?: ContentType.Text.Plain
        }

        else -> contentType
    }

    return if (result.charset() == null && result.match(ContentType.Text.Any)) {
        result.withCharset(Charsets.UTF_8)
    } else {
        result
    }
}
