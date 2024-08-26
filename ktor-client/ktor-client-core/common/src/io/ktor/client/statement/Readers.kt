/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.utils.io.*
import kotlinx.io.*

/**
 * Reads exactly [count] bytes of the [HttpResponse.rawContent].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readBytes(count: Int): ByteArray = ByteArray(count).also {
    rawContent.readFully(it)
}

/**
 * Reads the raw payload of the HTTP response as a byte array.
 *
 * This method reads the raw payload of the HTTP response as a byte array.
 * The raw payload is the content
 * of the response that hasn't gone through any interceptors from the HttpResponsePipeline.
 * The content will retain its original
 * compression or encoding as received from the server.
 *
 * @return the raw payload of the HTTP response as a byte array
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readRawBytes(): ByteArray = rawContent.readRemaining().readByteArray()

/**
 * Reads the raw payload of the HTTP response as a byte array.
 *
 * This method reads the raw payload of the HTTP response as a byte array.
 * The raw payload is the content of the response that hasn't gone through any interceptors.
 * The content will retain its original compression or encoding as received from the server.
 *
 * If you need to read the content as decoded bytes, use the [bodyAsBytes()] method instead.
 *
 * @return the raw payload of the HTTP response as a byte array
 */
@OptIn(InternalAPI::class)
@Deprecated("This method was renamed to readRawBytes() to reflect what it does.", ReplaceWith("readRawBytes()"))
public suspend fun HttpResponse.readBytes(): ByteArray = rawContent.readRemaining().readByteArray()

/**
 * Efficiently discards the remaining bytes of [HttpResponse.rawContent].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.discardRemaining() {
    rawContent.discard()
}
