/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class RequestTests : TestWithKtor() {

    override val server = embeddedServer(CIO, serverPort) {
        routing {
            get("/delay") {
                val delay = call.parameters.getOrFail("delay").toLong()
                delay(delay)
                call.respondText("OK")
            }

            post("/echo") {
                val readChannel = call.receive<ByteReadChannel>()
                call.respond(
                    HttpStatusCode.OK,
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType
                            get() = call.request.contentType()
                        override val contentLength: Long?
                            get() = call.request.header(HttpHeaders.ContentLength)?.toLong()

                        override fun readFrom(): ByteReadChannel {
                            return readChannel
                        }
                    }
                )
            }
        }
    }

    @Test
    fun testReusingRequestBuilderOnMultipleClients() {
        val requestBuilder = HttpRequestBuilder()
        requestBuilder.url.takeFrom("$testUrl/delay?delay=500")

        val clientSuccess = HttpClient(Java) {
            install(HttpTimeout) {
                requestTimeoutMillis = 1000
            }
        }
        val clientWithRequestTimeout = HttpClient(Java) {
            install(HttpTimeout) {
                requestTimeoutMillis = 100
            }
        }
        val clientWithConnectTimeout = HttpClient(Java) {
            install(HttpTimeout) {
                connectTimeoutMillis = 1
            }
        }

        runBlocking {
            val response = clientSuccess.get(requestBuilder).body<String>()
            assertEquals("OK", response)

            assertFailsWith<HttpRequestTimeoutException> {
                clientWithRequestTimeout.get(requestBuilder).body<String>()
            }

            assertFailsWith<ConnectTimeoutException> {
                clientWithConnectTimeout.get(
                    requestBuilder.apply {
                        url {
                            takeFrom("https://google.com")
                        }
                    }
                ).body<String>()
            }
        }
    }

    @Test
    fun testRequestBodyPosting() {
        val payload = "ktor"

        val response = HttpClient(Java).use { client ->
            runBlocking {
                client.post("$testUrl/echo") {
                    contentType(ContentType.Text.Plain)
                    setBody(ByteReadChannel(payload))
                }.body<String>()
            }
        }

        assertEquals(payload, response)
    }
}
