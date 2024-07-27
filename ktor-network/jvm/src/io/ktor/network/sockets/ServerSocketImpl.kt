/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.selector.eventgroup.*
import io.ktor.network.selector.eventgroup.Connection
import kotlinx.coroutines.*
import java.net.*
import java.nio.channels.*

@Suppress("BlockingMethodInNonBlockingContext")
internal class ServerSocketImpl(
    private val registeredServerChannel: RegisteredServerChannel,
    private val selector: SelectorManager,
) : ServerSocket, Selectable by SelectableBase(registeredServerChannel.channel) {
    override val channel: ServerSocketChannel get() = registeredServerChannel.channel

    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val socketContext: CompletableJob = Job()

    override val localAddress: SocketAddress
        get() {
            val localAddress = if (java7NetworkApisAvailable) {
                channel.localAddress
            } else {
                channel.socket().localSocketAddress
            }
            return localAddress.toSocketAddress()
        }

    override suspend fun accept(): Socket {
        return registeredServerChannel.acceptConnection { nioChannel ->
            if (localAddress is InetSocketAddress) {
                if (java7NetworkApisAvailable) {
                    nioChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
                } else {
                    nioChannel.socket().tcpNoDelay = true
                }
            }
        }.toSocket()
    }

    private fun Connection.toSocket(): Socket {
        return ServerConnectionBasedSocket(this, selector)
    }

    override fun close() {
        try {
            try {
                channel.close()
            } finally {
                selector.notifyClosed(this)
            }

            socketContext.complete()
        } catch (cause: Throwable) {
            socketContext.completeExceptionally(cause)
        }
    }

    override fun dispose() {
        super.dispose()
    }
}
