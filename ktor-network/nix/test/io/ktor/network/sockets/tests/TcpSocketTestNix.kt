/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlinx.io.*
import platform.posix.*
import kotlin.test.*

class TcpSocketTestNix {

    @Test
    fun testDescriptorClose() = testSuspend {
        val selector = SelectorManager()
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        socket.close()
        selector.close()

        selector.coroutineContext[Job]?.join()

        val isDescriptorValid = fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
        check(!isDescriptorValid) { "Descriptor was not closed" }
    }

    @Test
    fun testDescriptorCloseWithClientAndReadChannel() = testSuspend {
        val selector = SelectorManager()
        val tcp = aSocket(selector).tcp()

        val server = tcp.bind(InetSocketAddress("127.0.0.1", 0))
        val serverDescriptor = (server as TCPServerSocketNative).selectable.descriptor

        val serverConnectionPromise = async {
            server.accept()
        }

        val port = (server.localAddress as InetSocketAddress).port
        val clientConnection = tcp.connect("127.0.0.1", port)
        val clientDescriptor = (clientConnection as TCPSocketNative).selectable.descriptor

        val serverConnection = serverConnectionPromise.await()
        val serverConnectionDescriptor = (serverConnection as TCPSocketNative).selectable.descriptor

        clientConnection.openReadChannel()
        serverConnection.openReadChannel()

        serverConnection.close()
        clientConnection.close()
        server.close()
        selector.close()

        selector.coroutineContext[Job]?.join()

        val isServerDescriptorValid = fcntl(serverDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerDescriptorValid) { "Server descriptor was not closed" }

        val isServerConnectionDescriptorValid = fcntl(serverConnectionDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerConnectionDescriptorValid) { "Server connection descriptor was not closed" }

        val isClientDescriptorValid = fcntl(clientDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isClientDescriptorValid) { "Client descriptor was not closed" }
    }

    @Test
    fun testDescriptorError() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))
        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        launch {
            // Closing the descriptor here while accept is busy in select, should fail the accept.
            close(descriptor)
        }

        assertFailsWith<IOException> {
            socket.accept()
        }

        socket.close()
    }

    @Test
    fun testDescriptorErrorDoesNotFailOtherSockets() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))
        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        val socket2 = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        launch {
            launch {
                // Closing the descriptor here while accept is busy in select, should fail the accept.
                // Other sockets (in this case socket2) should not fail.
                close(descriptor)
            }

            assertFailsWith<IOException> {
                socket.accept()
            }

            socket.close()
        }

        // As socket2 should not fail, the timeout is expected.
        withTimeoutOrNull(500) {
            socket2.accept()
        }

        socket.close()
        socket2.close()
    }
}
