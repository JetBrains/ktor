/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*

@OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
public suspend fun OutgoingContent.toByteArray(): ByteArray = when (this) {
    is OutgoingContent.ContentWrapper -> delegate().toByteArray()
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.ReadChannelContent -> readFrom().toByteArray()
    is OutgoingContent.WriteChannelContent -> {
        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            writeTo(channel)
            channel.close()
        }
        channel.toByteArray()
    }

    is OutgoingContent.ProtocolUpgrade, is OutgoingContent.NoContent -> EmptyArray
}

@Suppress("KDocMissingDocumentation", "DEPRECATION")
@OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
public suspend fun OutgoingContent.toByteReadPacket(): Source = when (this) {
    is OutgoingContent.ByteArrayContent -> ByteReadPacket(bytes())
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining()
    is OutgoingContent.WriteChannelContent -> {
        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            writeTo(channel)
            channel.close()
        }
        channel.readRemaining()
    }

    else -> ByteReadPacketEmpty
}

/**
 * Send error response.
 */
public fun MockRequestHandleScope.respondError(
    status: HttpStatusCode,
    content: String = status.description,
    headers: Headers = headersOf()
): HttpResponseData = respond(content, status, headers)

/**
 * Send ok response.
 */
public fun MockRequestHandleScope.respondOk(
    content: String = ""
): HttpResponseData = respond(content, HttpStatusCode.OK)

/**
 * Respond redirect with [location] in Location header.
 */
public fun MockRequestHandleScope.respondRedirect(
    location: String = ""
): HttpResponseData = respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, location))

/**
 * Send [HttpStatusCode.BadRequest] response.
 */
public fun MockRequestHandleScope.respondBadRequest(): HttpResponseData =
    respond("Bad Request", HttpStatusCode.BadRequest)

/**
 * Send response with specified string [content], [status] and [headers].
 */
public fun MockRequestHandleScope.respond(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): HttpResponseData =
    respond(ByteReadChannel(content.toByteArray(Charsets.UTF_8)), status, headers)

/**
 * Send response with specified bytes [content], [status] and [headers].
 */
public fun MockRequestHandleScope.respond(
    content: ByteArray,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): HttpResponseData = respond(ByteReadChannel(content), status, headers)

/**
 * Send response with specified [ByteReadChannel] [content], [status] and [headers].
 */
public fun MockRequestHandleScope.respond(
    content: ByteReadChannel,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): HttpResponseData = HttpResponseData(
    status,
    GMTDate(),
    headers,
    HttpProtocolVersion.HTTP_1_1,
    content,
    callContext
)

private val EmptyArray = ByteArray(0)
