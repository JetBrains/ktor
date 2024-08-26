/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TCPSocketTest {

    @Test
    fun testEcho() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        val server = tcp.bind("127.0.0.1", 8000)

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect("127.0.0.1", 8000)
        val serverConnection = serverConnectionPromise.await()

        val clientOutput = clientConnection.openWriteChannel()
        try {
            clientOutput.writeStringUtf8("Hello, world\n")
            clientOutput.flush()
        } finally {
            clientOutput.flushAndClose()
        }

        val serverInput = serverConnection.openReadChannel()
        val message = serverInput.readUTF8Line()
        assertEquals("Hello, world", message)

        val serverOutput = serverConnection.openWriteChannel()
        try {
            serverOutput.writeStringUtf8("Hello From Server\n")
            serverOutput.flush()

            val clientInput = clientConnection.openReadChannel()
            val echo = clientInput.readUTF8Line()

            assertEquals("Hello From Server", echo)
        } finally {
            serverOutput.flushAndClose()
        }

        serverConnection.close()
        clientConnection.close()

        server.close()
    }

    @Test
    fun testEchoOverUnixSockets() = testSockets { selector ->
        if (!supportsUnixDomainSockets()) return@testSockets

        val socketPath = createTempFilePath("ktor-echo-test")

        val tcp = aSocket(selector).tcp()
        val server = tcp.bind(UnixSocketAddress(socketPath))

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect(UnixSocketAddress(socketPath))
        val serverConnection = serverConnectionPromise.await()

        val clientOutput = clientConnection.openWriteChannel()
        try {
            clientOutput.writeStringUtf8("Hello, world\n")
            clientOutput.flush()
        } finally {
            clientOutput.flushAndClose()
        }

        val serverInput = serverConnection.openReadChannel()
        val message = serverInput.readUTF8Line()
        assertEquals("Hello, world", message)

        val serverOutput = serverConnection.openWriteChannel()
        try {
            serverOutput.writeStringUtf8("Hello From Server\n")
            serverOutput.flush()

            val clientInput = clientConnection.openReadChannel()
            val echo = clientInput.readUTF8Line()

            assertEquals("Hello From Server", echo)
        } finally {
            serverOutput.flushAndClose()
        }

        serverConnection.close()
        clientConnection.close()

        server.close()

        removeFile(socketPath)
    }

    @Test
    fun testReadFromCancelledSocket() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        tcp.bind().use { server ->
            val serverConnection = async {
                server.accept()
            }

            val port = (server.localAddress as InetSocketAddress).port
            val client: Socket = tcp.connect("127.0.0.1", port)
            val readChannel = client.openReadChannel()
            serverConnection.await()

            client.cancel()

            assertFailsWith<CancellationException> {
                readChannel.readByte()
            }
        }
    }

    @Test
    fun testConnectToNonExistingSocket() = testSockets(timeout = 10.seconds) { selector ->
        assertFailsWith<IOException> {
            aSocket(selector)
                .tcp()
                .connect("127.0.0.1", 8001) // there should be no server active on this port
        }
    }

    @Test
    fun testDisconnect() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        val server = tcp.bind("127.0.0.1", 8003)

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect("127.0.0.1", 8003)
        val serverConnection = serverConnectionPromise.await()

        val serverInput = serverConnection.openReadChannel()

        // Need to make sure reading from server is done first, which will suspend because there is nothing to read.
        // Then close the connection from client side, which should cancel the reading because the socket disconnected.
        launch {
            delay(100)
            clientConnection.close()
        }

        assertFailsWith<EOFException> {
            serverInput.readByte()
        }

        serverConnection.close()
        server.close()
    }
}
