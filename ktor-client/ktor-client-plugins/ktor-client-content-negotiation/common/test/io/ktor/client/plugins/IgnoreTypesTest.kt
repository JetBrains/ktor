/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

class IgnoreTypesTest {
    val converter = object : ContentConverter {
        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            error("This should not be called")
        }

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            error("This should not be called")
        }
    }

    @OptIn(InternalAPI::class)
    val request = HttpRequestBuilder().apply {
        bodyType = typeInfo<String>()
        contentType(ContentType.Application.Json)
    }

    @Test
    fun testTriesToConvertString() = testSuspend {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, converter)
                clearIgnoredTypes()
            }
            engine {
                addHandler {
                    respond(
                        "OK",
                        HttpStatusCode.OK,
                        buildHeaders { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
                    )
                }
            }
        }

        val sendError = assertFails {
            client.post("/") {
                contentType(ContentType.Application.Json)
                setBody("BODY")
            }
        }
        assertEquals("This should not be called", sendError.message)

        val receiveError = assertFails {
            client.get("/").body<String>()
        }
        assertEquals("This should not be called", receiveError.message)
    }

    @Test
    fun testRequestWithIgnoredString() = testSuspend {
        val jsonBody = "{\"foo\":\"bar\"}"

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(jsonBody, request.body.toByteReadPacket().readText())
                    respond(
                        jsonBody,
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                    )
                }
            }
        }

        val response = client.get("/") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }

        assertEquals(jsonBody, response.bodyAsText())
    }
}
