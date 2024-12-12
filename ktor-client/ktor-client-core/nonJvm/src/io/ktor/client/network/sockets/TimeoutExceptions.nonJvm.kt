/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.network.sockets

import kotlinx.io.IOException

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public actual class ConnectTimeoutException actual constructor(
    message: String,
    cause: Throwable?
) : IOException(message, cause)

public actual open class InterruptedIOException : IOException()

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
public actual class SocketTimeoutException internal constructor(
    override val message: String?,
    override val cause: Throwable?,
    unit: Unit
) : InterruptedIOException()

public actual fun SocketTimeoutException(message: String, cause: Throwable?): SocketTimeoutException {
    return SocketTimeoutException(message, cause, Unit)
}
