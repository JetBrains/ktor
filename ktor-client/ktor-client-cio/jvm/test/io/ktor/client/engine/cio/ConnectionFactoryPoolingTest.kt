/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.time.Duration.Companion.milliseconds

@CoroutinesTimeout(5 * 1000)
class ConnectionFactoryPoolingTest {

    private lateinit var selector: SelectorManager
    private lateinit var factory: ConnectionFactory
    private lateinit var server: ServerSocket
    private lateinit var serverJob: Job
    private lateinit var serverAddress: InetSocketAddress

    init {
    }
    
    @BeforeEach
    fun setUp() {
        runBlocking {
            selector = SelectorManager(Dispatchers.Default)
            factory = ConnectionFactory(
                selector = selector, connectionsLimit = 10, addressConnectionsLimit = 5, keepAliveTime = 1000
            )

            // Set up local server
            server = aSocket(selector).tcp().bind(InetSocketAddress("localhost", 0))
            serverAddress = server.localAddress as InetSocketAddress
            serverJob = CoroutineScope(Dispatchers.IO).launch {

                while (isActive) {
                    val socket = server.accept()
                    launch {
                        val input = socket.openReadChannel()
                        val output = socket.openWriteChannel()
                        try {
                            while (isActive) {
                                val line = input.readUTF8Line() ?: break
                                output.writeStringUtf8("$line\n")
                                output.flush()
                            }
                        } finally {
                            socket.close()
                        }
                    }
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            serverJob.cancelAndJoin()

            server.close()
            selector.close()
        }
    }

    @Test
    fun `connect should create new connection when pool is empty`() = runBlocking {
        val socket = factory.connect(serverAddress)


        assertNotNull(socket)
        assertTrue(socket.isActive)
    }

    @Test
    fun `connect should reuse connection from pool`() = runBlocking {
        val socket1 = factory.connect(serverAddress)

        factory.release(socket1)
        val socket2 = factory.connect(serverAddress)

        assertEquals(socket1, socket2)
    }

    @Test
    fun `connect should create new connection when pooled connection is expired`() = runBlocking {
        val socket1 = factory.connect(serverAddress)

        factory.release(socket1)
        delay(1100.milliseconds) // Wait for the connection to expire
        val socket2 = factory.connect(serverAddress)

        assertNotEquals(socket1, socket2)
    }

    @Test
    fun `connect should respect address connections limit`() = runBlocking {
        val sockets = List(5) { factory.connect(serverAddress) }
        assertThrows<TimeoutCancellationException> {
            withTimeout(100) {
                factory.connect(serverAddress)
            }
        }

        sockets.forEach { factory.release(it) }
    }
}
