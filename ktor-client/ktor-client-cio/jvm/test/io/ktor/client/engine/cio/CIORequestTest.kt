/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import java.nio.channels.*
import kotlin.test.*
import kotlin.test.Ignore
import kotlin.test.Test

@CoroutinesTimeout(60_000)
class CIORequestTest : TestWithKtor() {
    private val testSize = 2 * 1024

    override val server: EmbeddedServer<*, *> = embeddedServer(Netty, serverPort) {
        routing {
            param("param") {
                get {
                    call.respond(call.parameters["param"]!!)
                }
            }
            get("/") {
                val longHeader = call.request.headers["LongHeader"]!!
                call.respond(
                    object : OutgoingContent.NoContent() {
                        override val headers: Headers = headersOf("LongHeader", longHeader)
                    }
                )
            }
            get("/echo") {
                call.respond("OK")
            }
            get("/delay") {
                delay(1000)
                call.respond("OK")
            }
        }
    }

    @Test
    fun engineUsesRequestTimeoutFromItsConfiguration() {
        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 10
                }
            }

            test { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
                }
            }
        }
    }

    @Test
    @Ignore
    fun testTimeoutPriority() {
        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 2000
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 1
                }
            }

            test { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
                }
            }
        }

        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 1
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 2000
                }
            }

            test { client ->
                client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
            }
        }
    }

    @Test
    fun longHeadersTest() = testWithEngine(CIO) {
        test { client ->
            val headerValue = "x".repeat(testSize)

            client.prepareGet {
                url(port = serverPort)
                header("LongHeader", headerValue)
            }.execute { response ->
                assertEquals(headerValue, response.headers["LongHeader"])
            }
        }
    }

    @Test
    fun testParameterWithoutPath() = testWithEngine(CIO) {
        test { client ->
            client.prepareGet {
                url(port = serverPort)
                parameter("param", "value")
            }.execute { response ->
                assertEquals("value", response.bodyAsText())
            }
        }
    }

    @Test
    fun testHangingTimeoutWithWrongUrl() = testWithEngine(CIO) {
        config {
            engine {
                endpoint {
                    connectTimeout = 1
                }
            }
        }

        test { client ->
            var fail: Throwable? = null
            for (i in 0..1000) {
                try {
                    client.get("http://something.wrong").body<String>()
                } catch (cause: Throwable) {
                    fail = cause
                }
            }

            assertNotNull(fail)
            if (fail !is ConnectTimeoutException && fail !is UnresolvedAddressException) {
                fail("Expected ConnectTimeoutException or UnresolvedAddressException, got $fail", fail)
            }
        }
    }
}
