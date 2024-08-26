/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class AutoHeadResponseTest {

    @Test
    fun testSimple() = testHeadApplication {
        routing {
            get("/") {
                call.response.header("M", "1")
                call.respond("Hello")
            }
        }

        client.get("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Hello", call.bodyAsText())
            assertEquals("1", call.headers["M"])
        }

        client.head("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("", call.bodyAsText())
            assertEquals("1", call.headers["M"])
        }
    }

    @Test
    fun testTextContent() = testHeadApplication {
        routing {
            get("/") {
                call.respondText("Hello")
            }
        }

        client.get("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Hello", call.bodyAsText())
            assertEquals("text/plain; charset=UTF-8", call.headers[HttpHeaders.ContentType])
        }

        client.head("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("", call.bodyAsText())
            assertEquals("text/plain; charset=UTF-8", call.headers[HttpHeaders.ContentType])
        }
    }

    @Test
    fun testCustomOutgoingContent() = testHeadApplication {
        routing {
            get("/") {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom() = ByteReadChannel("Hello".toByteArray())

                        override val headers: Headers
                            get() = Headers.build {
                                append("M", "2")
                            }
                    }
                )
            }
        }

        client.get("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Hello", call.bodyAsText())
            assertEquals("2", call.headers["M"])
            assertEquals("2", call.headers["m"])
        }

        client.head("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("", call.bodyAsText())
            assertEquals("2", call.headers["M"])
            assertEquals("2", call.headers["m"])
        }
    }

    @Test
    fun testWithStatusPages() = testHeadApplication {
        install(StatusPages) {
            exception<IllegalStateException> { call, code ->
                call.respondText("ISE: ${code.message}", status = HttpStatusCode.InternalServerError)
            }
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respondText(
                    "Not found",
                    status = HttpStatusCode.NotFound
                )
            }
        }

        routing {
            get("/page1") {
                call.respondText("page1 OK")
            }
            get("/page2") {
                throw IllegalStateException("page2 failed")
            }
        }

        // ensure with GET
        client.get("/page1").let { call ->
            assertEquals("page1 OK", call.bodyAsText())
        }

        client.get("/page2").let { call ->
            assertEquals(500, call.status.value)
            assertEquals("ISE: page2 failed", call.bodyAsText())
        }

        client.get("/page3").let { call ->
            assertEquals(404, call.status.value)
            assertEquals("Not found", call.bodyAsText())
        }

        // test HEAD itself
        client.head("/page1").let { call ->
            assertEquals("page1 OK".length.toString(), call.headers[HttpHeaders.ContentLength])
            assertEquals("", call.bodyAsText())
        }

        client.head("/page2").let { call ->
            assertEquals(500, call.status.value)
            assertEquals("ISE: page2 failed".length.toString(), call.headers[HttpHeaders.ContentLength])
            assertEquals("", call.bodyAsText())
        }

        client.head("/page3").let { call ->
            assertEquals(404, call.status.value)
            assertEquals("Not found".length.toString(), call.headers[HttpHeaders.ContentLength])
            assertEquals("", call.bodyAsText())
        }
    }

    @Test
    fun testDisposesContent() = testApplication {
        val channel = ByteChannel()

        install(AutoHeadResponse)
        routing {
            get("/test") {
                call.respond(channel)
            }
        }
        val response = client.head("test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(channel.isClosedForRead)
        assertTrue(channel.isClosedForWrite)
    }

    private fun testHeadApplication(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        install(AutoHeadResponse)
        block()
    }
}
