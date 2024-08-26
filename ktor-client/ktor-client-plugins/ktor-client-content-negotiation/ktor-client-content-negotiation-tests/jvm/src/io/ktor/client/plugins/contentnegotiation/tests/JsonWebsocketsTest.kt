/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation.tests

import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlin.test.*

abstract class JsonWebsocketsTest(private val converter: WebsocketContentConverter) {

    @Serializable
    data class User(val name: String, val id: Int)

    @Test
    open fun testJsonNullWithWebsocketsClient() = testApplication {
        install(io.ktor.server.websocket.WebSockets)
        routing {
            webSocket("/") {
                for (frame in incoming) {
                    assertEquals("null", (frame as Frame.Text).readText())
                    outgoing.send(frame)
                }
            }
        }

        createClient {
            install(WebSockets) {
                contentConverter = this@JsonWebsocketsTest.converter
            }
        }.ws("/") {
            val user: User? = null
            sendSerialized(user)
            val received = receiveDeserialized<User?>()
            assertNull(received)
        }
    }

    @Test
    open fun testJsonWithNullWebsocketsServer() = testApplication {
        install(io.ktor.server.websocket.WebSockets) {
            contentConverter = this@JsonWebsocketsTest.converter
        }
        routing {
            webSocket("/") {
                val user: User? = null
                sendSerialized(user)
                val received = receiveDeserialized<User?>()
                assertNull(received)
            }
        }

        createClient {
            install(WebSockets)
        }.ws("/") {
            for (frame in incoming) {
                assertEquals("null", (frame as Frame.Text).readText())
                outgoing.send(frame)
            }
        }
    }

    @Test
    open fun testJsonWithWebsocketsClient() = testApplication {
        install(io.ktor.server.websocket.WebSockets)
        routing {
            webSocket("/") {
                for (frame in incoming) {
                    assertEquals("""{"name":"user","id":123}""", (frame as Frame.Text).readText())
                    outgoing.send(frame)
                }
            }
        }

        createClient {
            install(WebSockets) {
                contentConverter = this@JsonWebsocketsTest.converter
            }
        }.ws("/") {
            val user = User("user", 123)
            sendSerialized(user)
            val received = receiveDeserialized<User>()
            assertEquals(user, received)
        }
    }

    @Test
    open fun testJsonWithWebsocketsServer() = testApplication {
        install(io.ktor.server.websocket.WebSockets) {
            contentConverter = this@JsonWebsocketsTest.converter
        }
        routing {
            webSocket("/") {
                val user = User("user", 123)
                sendSerialized(user)
                val received = receiveDeserialized<User>()
                assertEquals(user, received)
            }
        }

        createClient {
            install(WebSockets)
        }.ws("/") {
            for (frame in incoming) {
                assertEquals("""{"name":"user","id":123}""", (frame as Frame.Text).readText())
                outgoing.send(frame)
            }
        }
    }
}
