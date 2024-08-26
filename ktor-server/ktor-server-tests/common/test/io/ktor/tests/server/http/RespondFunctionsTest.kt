/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.io.*
import kotlin.test.*

class RespondFunctionsTest {
    @Test
    fun testRespondBytes() = testApplication {
        routing {
            get("/") {
                call.respondBytes(ByteArray(10) { it.toByte() })
            }
            get("/provider") {
                call.respondBytes { ByteArray(10) { it.toByte() } }
            }
            get("/bytes-writer") {
                call.respondBytesWriter(contentLength = 2) { writeByte(1); writeByte(2) }
            }
        }

        client.get("/").let { call ->
            assertEquals(
                "0, 1, 2, 3, 4, 5, 6, 7, 8, 9",
                call.bodyAsChannel().readRemaining().readByteArray().joinToString()
            )
            assertFalse(call.headers.contains(HttpHeaders.ContentType))
        }
        client.get("/provider").let { call ->
            assertEquals(
                "0, 1, 2, 3, 4, 5, 6, 7, 8, 9",
                call.bodyAsChannel().readRemaining().readByteArray().joinToString()
            )
            assertFalse(call.headers.contains(HttpHeaders.ContentType))
        }
        client.get("/bytes-writer").let { call ->
            assertEquals("1, 2", call.bodyAsChannel().readRemaining().readByteArray().joinToString())
            assertEquals("2", call.headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun testRespondWithTypeInfo() = testApplication {
        routing {
            get("respond") {
                call.respond("asd", typeInfo<String>())
            }
            get("respond-with-status") {
                call.respond(HttpStatusCode.NotFound, "asd", typeInfo<String>())
            }
        }
        application {
            install(
                createApplicationPlugin("checker") {
                    onCallRespond { _ ->
                        transformBody {
                            assertEquals(typeInfo<String>().type, String::class)
                            it
                        }
                    }
                }
            )
        }

        client.get("/respond")
        client.get("/respond-with-status")
    }

    @Test
    fun testRespondWithText() = testApplication {
        routing {
            get("json") {
                call.respondText("Hello", contentType = ContentType.Application.Json)
            }
            get("text") {
                call.respondText("Hello", contentType = ContentType.Text.Plain)
            }
        }

        client.get("/json").let {
            assertEquals("Hello", it.bodyAsText())
            assertEquals(ContentType.Application.Json, it.contentType())
        }
        client.get("/text").let {
            assertEquals("Hello", it.bodyAsText())
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), it.contentType())
        }
    }
}
