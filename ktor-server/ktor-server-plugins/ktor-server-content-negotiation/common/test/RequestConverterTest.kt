/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.reflect.*
import kotlin.test.*

class RequestConverterTest {

    @Test
    fun testIgnoreType() = testApplication {
        var used = false

        install(ContentNegotiation) {
            ignoreType<NonSerializableClass>()
            register(
                ContentType.Application.Json,
                object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent? {
                        error("Not yet implemented")
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any? {
                        used = true
                        if (typeInfo.kotlinType == typeOf<SerializableClass>()) return SerializableClass()
                        return null
                    }
                }
            )
        }

        routing {
            post("/foo") {
                val result: String = try {
                    call.receive<NonSerializableClass>()
                    "OK"
                } catch (cause: Throwable) {
                    cause.message ?: cause.toString()
                }
                call.respondText(result)
            }
            post("/bar") {
                val result: String = try {
                    call.receive<SerializableClass>()
                    "OK"
                } catch (cause: Throwable) {
                    cause.message ?: cause.toString()
                }
                call.respondText(result)
            }
        }

        val responseFoo = client.post("/foo") {
            contentType(ContentType.Application.Json)
        }.bodyAsText()

        try {
            assertEquals(
                "Cannot transform this request's content to io.ktor.server.plugins.contentnegotiation.NonSerializableClass", // ktlint-disable max-line-length
                responseFoo
            )
        } catch (cause: Throwable) {
            // because of JS/Wasm
            assertEquals(
                "Cannot transform this request's content to NonSerializableClass",
                responseFoo
            )
        }
        assertFalse(used)

        val responseBar = client.post("/bar") {
            contentType(ContentType.Application.Json)
        }

        val body = responseBar.bodyAsText()
        assertEquals("OK", body)
        assertTrue(used)
    }
}

internal class NonSerializableClass

internal class SerializableClass
