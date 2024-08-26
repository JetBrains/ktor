/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.plugins.contentnegotiation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.io.*
import java.io.*
import kotlin.test.*

class ContentNegotiationJvmTest {
    private val alwaysFailingConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            fail("This converter should be never started for send")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            fail("This converter should be never started for receive")
        }
    }

    @Test
    fun testReceiveInputStreamTransformedByDefault() = testApplication {
        application {
            install(ContentNegotiation) {
                // Order here matters. The first registered content type matching the Accept header will be chosen.
                register(ContentType.Any, alwaysFailingConverter)
            }

            routing {
                post("/input-stream") {
                    val size = call.receive<InputStream>().readBytes().size
                    call.respondText("bytes from IS: $size")
                }
                post("/multipart") {
                    val multipart = call.receiveMultipart()
                    val parts = mutableListOf<PartData>()
                    multipart.forEachPart {
                        parts.add(it)
                    }

                    call.respondText("parts: ${parts.map { it.name }}")
                }
            }
        }

        assertEquals("bytes from IS: 3", client.post("/input-stream") { setBody("123") }.bodyAsText())
        client.post("/multipart") {
            setBody(
                buildMultipart(
                    "my-boundary",
                    listOf(
                        PartData.FormItem(
                            "test",
                            {},
                            headersOf(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition(
                                    "form-data",
                                    listOf(
                                        HeaderValueParam("name", "field1")
                                    )
                                ).toString()
                            )
                        )
                    )
                )
            )
            header(
                HttpHeaders.ContentType,
                ContentType.MultiPart.FormData.withParameter("boundary", "my-boundary").toString()
            )
        }.let { response ->
            assertEquals("parts: [field1]", response.bodyAsText())
        }
    }

    @Test
    fun testRespondInputStream() = testApplication {
        application {
            routing {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, alwaysFailingConverter)
                }
                get("/") {
                    call.respond(ByteArrayInputStream("""{"x": 123}""".toByteArray()))
                }
            }
        }
        val response = client.get("/").bodyAsText()
        assertEquals("""{"x": 123}""", response)
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun buildMultipart(
    boundary: String,
    parts: List<PartData>
): ByteReadChannel = GlobalScope.writer(Dispatchers.IO) {
    if (parts.isEmpty()) return@writer

    try {
        append("\r\n\r\n")
        parts.forEach {
            append("--$boundary\r\n")
            for ((key, values) in it.headers.entries()) {
                append("$key: ${values.joinToString(";")}\r\n")
            }
            append("\r\n")
            append(
                when (it) {
                    is PartData.FileItem -> {
                        channel.writeFully(it.provider().readRemaining().readByteArray())
                        ""
                    }
                    is PartData.BinaryItem -> {
                        channel.writeFully(it.provider().readByteArray())
                        ""
                    }
                    is PartData.FormItem -> it.value
                    is PartData.BinaryChannelItem -> {
                        it.provider().copyTo(channel)
                        ""
                    }
                }
            )
            append("\r\n")
        }

        append("--$boundary--\r\n")
    } finally {
        parts.forEach { it.dispose() }
    }
}.channel

private suspend fun WriterScope.append(str: String, charset: Charset = Charsets.UTF_8) {
    channel.writeFully(str.toByteArray(charset))
}
