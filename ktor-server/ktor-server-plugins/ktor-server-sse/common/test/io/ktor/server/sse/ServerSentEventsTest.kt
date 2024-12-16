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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class ServerSentEventsTest {

    @Test
    fun testSingleEvents() = testApplication {
        install(SSE)
        routing {
            sse("/hello") {
                send(ServerSentEvent("world", event = "send", id = "100", retry = 1000, comments = "comment"))
            }
        }

        val client = createSseClient()
        val expected = """
            data: world
            event: send
            id: 100
            retry: 1000
            : comment
            
        """.trimIndent()
        val actual = StringBuilder()
        client.sse("/hello") {
            val event = incoming.single()
            assertEquals("world", event.data)
            assertEquals("send", event.event)
            assertEquals("100", event.id)
            assertEquals(1000, event.retry)
            assertEquals("comment", event.comments)
            actual.append(event)
        }
        assertEquals(expected.lines(), actual.toString().lines())
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

    @Test
    fun testMultilineData() = testApplication {
        install(SSE)
        routing {
            sse("/multiline-data") {
                send(
                    """
                    First Line
                    Second Line
                    Third Line
                    """.trimIndent()
                )
            }

            sse("/one-event-data") {
                send(
                    """
                    First Line
                    
                    data: Third Line
                    """.trimIndent()
                )
            }
        }

        val client = createSseClient()

        val expectedMultilineData = """
            data: First Line
            data: Second Line
            data: Third Line

        """.trimIndent()
        val actualMultilineData = StringBuilder()
        client.sse("/multiline-data") {
            incoming.collect {
                actualMultilineData.append(it.toString())
            }
        }
        assertEquals(expectedMultilineData.lines(), actualMultilineData.toString().lines())

        val expectedOneEventData = """
            data: First Line
            data: 
            data: data: Third Line
            
        """.trimIndent()
        val actualOneEventData = StringBuilder()
        client.sse("/one-event-data") {
            incoming.collect {
                actualOneEventData.append(it.toString())
            }
        }
        assertEquals(expectedOneEventData.lines(), actualOneEventData.toString().lines())
    }

    class Person1(val age: Int)
    class Person2(val number: Int)

    @Test
    fun testSerializerInRoute() = testApplication {
        install(SSE)
        routing {
            sse("/person", serialize = { typeInfo, data ->
                when (typeInfo.type) {
                    Person1::class -> {
                        "Age ${(data as Person1).age}"
                    }

                    else -> {
                        data.toString()
                    }
                }
            }) {
                repeat(10) {
                    send(Person1(it))
                }
            }
        }

        val client = createSseClient()

        client.sse("/person") {
            incoming.collectIndexed { i, person ->
                assertEquals("Age $i", person.data)
            }
        }
    }

    @Test
    fun testDifferentSerializers() = testApplication {
        install(SSE)
        routing {
            sse(serialize = { typeInfo, data ->
                when (typeInfo.type) {
                    Person1::class -> {
                        "Age ${(data as Person1).age}"
                    }

                    Person2::class -> {
                        "Number ${(data as Person2).number}"
                    }

                    else -> {
                        data.toString()
                    }
                }
            }) {
                send(Person1(22))
                send(Person2(123456))
            }
        }

        val client = createSseClient()
        client.sse {
            var first = true
            incoming.collect {
                if (first) {
                    assertEquals("Age 22", it.data)
                    first = false
                } else {
                    assertEquals("Number 123456", it.data)
                }
            }
        }
    }

    @Serializable
    data class Customer(val id: Int, val firstName: String, val lastName: String)

    @Serializable
    data class Product(val id: Int, val prices: List<Int>)

    @Test
    fun testJsonSerializer() = testApplication {
        install(SSE)
        routing {
            sse("/json", serialize = { typeInfo, it ->
                val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                Json.encodeToString(serializer, it)
            }) {
                send(Customer(0, "Jet", "Brains"))
                send(Product(0, listOf(100, 200)))
            }
        }

        assertEquals(
            "data: {\"id\":0,\"firstName\":\"Jet\",\"lastName\":\"Brains\"}\r\n\r\n" +
                "data: {\"id\":0,\"prices\":[100,200]}",
            client.get("/json").bodyAsText().trim()
        )
    }

    @Test
    fun testHeartbeat() = testApplication {
        install(SSE)
        routing {
            sse {
                heartbeat {
                    duration = 10.milliseconds
                    event = ServerSentEvent("heartbeat")
                }

                repeat(4) {
                    send("Hello")
                    delay(10.milliseconds)
                }
            }
        }

        val client = createSseClient()

        var hellos = 0
        var heartbeats = 0
        withTimeout(5_000) {
            client.sse {
                incoming.collect { event ->
                    when (event.data) {
                        "Hello" -> hellos++
                        "heartbeat" -> heartbeats++
                    }
                    if (hellos > 3 && heartbeats > 3) {
                        cancel()
                    }
                }
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
