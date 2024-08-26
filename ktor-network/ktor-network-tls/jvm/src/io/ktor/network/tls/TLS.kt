/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 *
 * The coroutine context passed here will receive errors when there are no other handlers to process it (for example,
 * in case of a shutdown during a TLS handshake). The context may also be used for cancellation.
 *
 * Note that the context passed here is rarely a child of the scope in which the method is called, because it is not
 * usually a decomposition of the parent task. If it is a child, errors may be propogated to the parent's coroutine
 * exception handler rather than being caught and handled via a try-catch block.
 */
public actual suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    val reader = openReadChannel()
    val writer = openWriteChannel()

    return try {
        openTLSSession(this, reader, writer, config, coroutineContext)
    } catch (cause: Throwable) {
        reader.cancel(cause)
        writer.close(cause)
        close()
        throw cause
    }
}

/**
 * Make [Socket] connection secure with TLS.
 *
 * The coroutine context passed here will receive errors when there are no other handlers to process it (for example,
 * in case of a shutdown during a TLS handshake). The context may also be used for cancellation.
 *
 * Note that the context passed here is rarely a child of the scope in which the method is called, because it is not
 * usually a decomposition of the parent task. If it is a child, errors may be propogated to the parent's coroutine
 * exception handler rather than being caught and handled via a try-catch block.
 */
public suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    trustManager: X509TrustManager? = null,
    randomAlgorithm: String = "NativePRNGNonBlocking",
    cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites,
    serverName: String? = null
): Socket = tls(coroutineContext) {
    this.trustManager = trustManager
    this.random = SecureRandom.getInstance(randomAlgorithm)
    this.cipherSuites = cipherSuites
    this.serverName = serverName
}

/**
 * Make [Socket] connection secure with TLS configured with [block].
 *
 * The coroutine context passed here will receive errors when there are no other handlers to process it (for example,
 * in case of a shutdown during a TLS handshake). The context may also be used for cancellation.
 *
 * Note that the context passed here is rarely a child of the scope in which the method is called, because it is not
 * usually a decomposition of the parent task. If it is a child, errors may be propogated to the parent's coroutine
 * exception handler rather than being caught and handled via a try-catch block.
 */
public actual suspend fun Socket.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket =
    tls(coroutineContext, TLSConfigBuilder().apply(block).build())
