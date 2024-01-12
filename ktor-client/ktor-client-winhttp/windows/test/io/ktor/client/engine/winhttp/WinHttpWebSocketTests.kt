/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class WinHttpWebSocketTests {

    private val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"

    @Test
    fun testEcho() {
        val client = HttpClient(WinHttp) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text("Hello, world"))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("Hello, world", actual.readText())
            }
        }
    }

    @Test
    fun testEmptyFrame() {
        val client = HttpClient(WinHttp) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text(""))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("", actual.readText())
            }
        }
    }
}
