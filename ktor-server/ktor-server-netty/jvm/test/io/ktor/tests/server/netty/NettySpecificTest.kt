/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.net.*
import java.util.concurrent.*
import kotlin.test.*

class NettySpecificTest {

    @Test
    fun testNoLeakWithoutStartAndStop() {
        repeat(100000) {
            embeddedServer(Netty, applicationProperties { })
        }
    }

    @Test
    fun testStartOnUsedPort() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val server = embeddedServer(Netty, port) {}

        try {
            server.start(wait = false)
        } catch (_: BindException) {
        }

        assertTrue(server.engine.bootstraps.all { (it.config().group() as ExecutorService).isTerminated })
    }

    @Test
    fun testStartMultipleConnectorsOnUsedPort() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val socket2 = ServerSocket(0)
        val port2 = socket2.localPort
        val host = "0.0.0.0"

        socket.close()

        try {
            val environment = applicationEnvironment()

            val server = embeddedServer(Netty, environment, {
                connector {
                    this.port = port
                    this.host = host
                }
                connector {
                    this.port = port2
                    this.host = host
                }
            })

            try {
                server.start(wait = false)
            } catch (_: BindException) {
            }

            assertTrue(server.engine.bootstraps.all { (it.config().group() as ExecutorService).isTerminated })
        } finally {
            socket2.close()
        }
    }
}
