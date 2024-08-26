/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
public expect suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket

/**
 * Make [Socket] connection secure with TLS.
 *
 * TODO: report YT issue
 */
public suspend fun Socket.tls(coroutineContext: CoroutineContext): Socket = tls(coroutineContext) {}

/**
 * Make [Socket] connection secure with TLS configured with [block].
 */
public expect suspend fun Socket.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
public suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    return try {
        openTLSSession(socket, input, output, config, coroutineContext)
    } catch (cause: Throwable) {
        input.cancel(cause)
        output.close(cause)
        socket.close()
        throw cause
    }
}

/**
 * Make [Socket] connection secure with TLS.
 */
public suspend fun Connection.tls(coroutineContext: CoroutineContext): Socket = tls(coroutineContext) {}

/**
* Make [Socket] connection secure with TLS configured with [block].
*/
public suspend fun Connection.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket =
    tls(coroutineContext, TLSConfigBuilder().apply(block).build())
