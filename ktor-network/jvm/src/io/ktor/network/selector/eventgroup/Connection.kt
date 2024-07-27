/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import java.nio.channels.SocketChannel

/**
 * Allows to perform read and write operations on the socket channel,
 * which will be submitted as tasks to the event loop and will be suspended until
 * they will be executed in the context of the event loop
 */
internal interface Connection {
    val channel: SocketChannel

    suspend fun <T> performRead(body: suspend (SocketChannel) -> T): T

    suspend fun <T> performWrite(body: suspend (SocketChannel) -> T): T

    fun close()
}
