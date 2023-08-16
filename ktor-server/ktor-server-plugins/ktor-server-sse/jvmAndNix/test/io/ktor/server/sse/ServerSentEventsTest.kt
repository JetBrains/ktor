/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sse

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.sse.*
import kotlinx.coroutines.*
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

        client.get("/hello").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream.toString(), headers[HttpHeaders.ContentType])
            assertEquals("data: world", bodyAsText().trim())
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

        client.get("/events").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream.toString(), headers[HttpHeaders.ContentType])
            val events = bodyAsText().lines()
            assertEquals(201, events.size)
            for (i in 0 until 100) {
                assertEquals("data: event $i", events[i * 2])
                assertTrue { events[i * 2 + 1].isEmpty() }
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

        client.get("/events").apply {
            val events = bodyAsText().lines()
            assertEquals(201, events.size)
            for (i in 0 until 100) {
                assertEquals("data: event $i", events[i * 2])
                assertTrue { events[i * 2 + 1].isEmpty() }
            }
        }
        client.get("/events").apply {
            val events = bodyAsText().lines()
            assertEquals(201, events.size)
            for (i in 0 until 100) {
                assertEquals("data: event $i", events[i * 2])
                assertTrue { events[i * 2 + 1].isEmpty() }
            }
        }
    }
}
