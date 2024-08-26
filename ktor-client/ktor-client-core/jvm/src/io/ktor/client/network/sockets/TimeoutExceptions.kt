/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.network.sockets

import java.net.*

/**
 * This exception is thrown in case of connect timeout exceeded.
 */
@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual class ConnectTimeoutException actual constructor(
    message: String,
    override val cause: Throwable?
) : ConnectException(message)

internal actual typealias InterruptedIOException = java.io.InterruptedIOException

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
public actual typealias SocketTimeoutException = java.net.SocketTimeoutException

public actual fun SocketTimeoutException(message: String, cause: Throwable?): SocketTimeoutException =
    SocketTimeoutException(message).apply { initCause(cause) }
