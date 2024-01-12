/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class DefaultWebSocketTestJvm : BaseTest() {

    @OptIn(InternalAPI::class)
    @Test
    fun testOutgoingCapacity(): Unit = runTest {
        val capacity = 1000 // more than BBC can buffer

        System.setProperty("io.ktor.websocket.outgoingChannelCapacity", "$capacity")

        val parent = Job()
        val client2server = ByteChannel()
        val server2client = ByteChannel()

        val server = DefaultWebSocketSession(
            RawWebSocket(client2server, server2client, coroutineContext = parent),
            -1L,
            1000L
        ).apply { start() }

        val client = RawWebSocket(server2client, client2server, coroutineContext = parent)

        repeat(capacity) {
            server.outgoing.send(Frame.Text("$it"))
        }
        repeat(capacity) {
            assertEquals("$it", (client.incoming.receive() as Frame.Text).readText())
        }

        client.close()
        server.closeReason.await()
        client.incoming.receive()
        ensureCompletion(parent, client2server, server2client, server, client)

        System.clearProperty("io.ktor.websocket.outgoingChannelCapacity")

        server.cancel()
        client.cancel()
        client2server.cancel()
        server2client.cancel()
        parent.cancel()
    }
}
