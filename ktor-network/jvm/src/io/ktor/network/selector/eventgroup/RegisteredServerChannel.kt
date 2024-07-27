/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import java.net.Socket
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Represents a server channel registered to an event loop with OP_ACCEPT interest
 */
internal interface RegisteredServerChannel {
    val channel: ServerSocketChannel

    /**
     * Allows to accept connections on the server socket channel
     */
    suspend fun acceptConnection(configure: (SocketChannel) -> Unit = {}): Connection
}
