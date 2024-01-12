/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

/**
 * Reads exactly [count] bytes of the [HttpResponse.content].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readBytes(count: Int): ByteArray = ByteArray(count).also {
    content.readFully(it)
}

/**
 * Reads the whole [HttpResponse.content] if `Content-Length` is specified.
 * Otherwise, it just reads one byte.
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readBytes(): ByteArray = content.readRemaining().readBytes()

/**
 * Efficiently discards the remaining bytes of [HttpResponse.content].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.discardRemaining() {
    content.discard()
}
