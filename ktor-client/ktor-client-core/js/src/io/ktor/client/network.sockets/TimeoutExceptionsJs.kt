/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.network.sockets

import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public actual class ConnectTimeoutException actual constructor(
    message: String,
    cause: Throwable?
) : IOException(message, cause)

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
public actual class SocketTimeoutException actual constructor(
    message: String,
    cause: Throwable?
) : IOException(message, cause)

/**
 * Creates [ByteChannel] that maps close exceptions (close the channel with [SocketTimeoutException] if asked to
 * close it with [SocketTimeoutException]).
 */
@Suppress("DEPRECATION")
internal actual fun ByteChannelWithMappedExceptions(request: HttpRequestData): ByteChannel = ByteChannel()
