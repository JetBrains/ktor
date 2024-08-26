/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sse

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class ServerSentEventsTest {

    @Test
    fun testSingleEvents() = testApplication {
        install(SSE)
        routing {
            sse("/hello") {
                send(ServerSentEvent("world"))
            }
        }

        val client = createSseClient()
        client.sse("/hello") {
            assertEquals("world", incoming.single().data)
        }
    }

    @Test
    fun testEvents() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                repeat(100) {
                    send(ServerSentEvent("event $it"))
                }
            }
        }

        val client = createSseClient()
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
    }

    @Test
    fun testChannelsOfEvents() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                launch {
                    repeat(100) {
                        send(ServerSentEvent("channel-1 $it"))
                    }
                }
                launch {
                    repeat(100) {
                        send(ServerSentEvent("channel-2 $it"))
                    }
                }
            }
        }

        client.get("/events").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream.toString(), headers[HttpHeaders.ContentType])
            val events = bodyAsText().lines()
            assertEquals(401, events.size)
            for (i in 0 until 100) {
                assertContains(events, "data: channel-1 $i")
                assertContains(events, "data: channel-2 $i")
            }
        }
    }

    @Test
    fun testSeveralClients() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                repeat(100) {
                    send(ServerSentEvent("event $it"))
                }
            }
        }

        val client = createSseClient()
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
    }

    @Test
    fun testNoDuplicateHeader() = testApplication {
        install(SSE)
        routing {
            sse { }
        }

        val client = createSseClient()
        client.sse {
            call.response.headers.forEach { _, values ->
                assertEquals(1, values.size)
            }
        }
    }

    private fun ApplicationTestBuilder.createSseClient(): HttpClient {
        val client = createClient {
            install(io.ktor.client.plugins.sse.SSE)
        }
        return client
    }
}
