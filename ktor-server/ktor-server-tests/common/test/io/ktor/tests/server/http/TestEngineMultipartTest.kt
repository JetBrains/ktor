/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlin.test.*

class TestEngineMultipartTest {
    private val boundary = "***bbb***"
    private val contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    @Test
    fun testNonMultipart() = testMultiParts({
        assertNull(it, "it should be no multipart data")
    }, setup = {})

    @Test
    fun testMultiPartsPlainItemBinary(): TestResult {
        val bytes = ByteArray(256) { it.toByte() }
        return testMultiPartsFileItemBase(
            filename = "file.bin",
            provider = { ByteReadChannel(bytes) },
            extraFileAssertions = { file ->
                assertEquals(
                    hex(bytes),
                    hex(file.provider().readRemaining().readByteArray())
                )
            }
        )
    }

    @Test
    fun testMultiPartsFileItemText(): TestResult {
        val string = "file content with unicode 🌀 : здороваться : 여보세요 : 你好 : ñç"
        return testMultiPartsFileItemBase(
            filename = "file.txt",
            provider = { ByteReadChannel(string.toByteArray()) },
            extraFileAssertions = { file -> assertEquals(string, file.provider().readRemaining().readText()) }
        )
    }

    @Test
    fun testMultiPartsFileItem(): TestResult {
        val bytes = ByteArray(256) { it.toByte() }

        return testMultiParts({
            assertNotNull(it, "it should be multipart data")
            val parts = it.readAllParts()

            assertEquals(1, parts.size)
            val file = parts[0] as PartData.FileItem

            assertEquals("fileField", file.name)
            assertEquals("file.bin", file.originalFileName)
            assertEquals(hex(bytes), hex(file.provider().readRemaining().readByteArray()))

            file.dispose()
        }) {
            header(HttpHeaders.ContentType, contentType.toString())
            val partHeaders = headersOf(
                HttpHeaders.ContentDisposition,
                ContentDisposition.File
                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                    .withParameter(ContentDisposition.Parameters.FileName, "file.bin")
                    .toString()
            )
            val parts = listOf(
                PartData.FileItem(
                    provider = { ByteReadChannel(bytes) },
                    dispose = {},
                    partHeaders =
                    partHeaders
                )
            )

            setBody(buildMultipart(boundary, parts))
        }
    }

    @Test
    fun testMultiPartShouldFail() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                try {
                    call.receiveMultipart().readAllParts()
                } catch (error: Throwable) {
                    fail("This pipeline shouldn't finish successfully")
                }
            }
        }

        assertFailsWith<AssertionError> {
            client.post("/")
        }
    }

    @Test
    fun testMultipartIsNotTruncated() {
        if (!PlatformUtils.IS_JVM) return

        testApplication {
            routing {
                post {
                    val multipart = call.receiveMultipart(formFieldLimit = 60 * 1024 * 1024)
                    while (true) {
                        val part = multipart.readPart() ?: break
                        when (part) {
                            is PartData.FileItem -> {
                                part.provider().readRemaining().readText()
                            }

                            is PartData.FormItem -> {
                                part.value
                            }

                            is PartData.BinaryChannelItem -> {
                                part.provider().readRemaining().readText()
                            }

                            is PartData.BinaryItem -> {
                                part.provider().readByteArray()
                            }
                        }
                        part.dispose()
                    }
                    call.respondText("OK")
                }
            }

            val response =
                client.post {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("data", "a".repeat(42 * 1024 * 1024))
                            }
                        )
                    )
                }

            if (response.status == HttpStatusCode.UnsupportedMediaType) {
                return@testApplication
            }
        }
    }

    @Test
    fun testMultipartBigger65536Fails() {
        if (!PlatformUtils.IS_JVM) return

        testApplication {
            routing {
                post {
                    val multipart = call.receiveMultipart()
                    while (true) {
                        val part = multipart.readPart() ?: break
                        when (part) {
                            is PartData.FileItem -> {
                                part.provider().readRemaining().readText()
                            }

                            is PartData.FormItem -> {
                                part.value
                            }

                            is PartData.BinaryChannelItem -> {
                                part.provider().readRemaining().readText()
                            }

                            is PartData.BinaryItem -> {
                                part.provider().readByteArray()
                            }
                        }
                        part.dispose()
                    }
                }
            }

            assertFailsWith<IOException> {
                client.post {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("data", "a".repeat(42 * 1024 * 1024))
                            }
                        )
                    )
                }
            }
        }
    }

    private fun testMultiParts(
        asserts: suspend (MultiPartData?) -> Unit,
        setup: HttpRequestBuilder.() -> Unit
    ) = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                if (call.request.isMultipart()) {
                    asserts(call.receiveMultipart())
                } else {
                    asserts(null)
                }
            }
        }

        client.post("/", setup)
    }

    private fun testMultiPartsFileItemBase(
        filename: String,
        provider: () -> ByteReadChannel,
        extraFileAssertions: suspend (file: PartData.FileItem) -> Unit
    ) = testMultiParts({
        assertNotNull(it, "it should be multipart data")
        val parts = it.readAllParts()

        assertEquals(1, parts.size)
        val file = parts[0] as PartData.FileItem

        assertEquals("fileField", file.name)
        assertEquals(filename, file.originalFileName)
        extraFileAssertions(file)

        file.dispose()
    }, setup = {
        header(HttpHeaders.ContentType, contentType.toString())
        setBody(
            buildMultipart(
                boundary,
                listOf(
                    PartData.FileItem(
                        provider = provider,
                        dispose = {},
                        partHeaders =
                        headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File
                                .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                .withParameter(ContentDisposition.Parameters.FileName, filename)
                                .toString()
                        )
                    )
                )
            )
        )
    })
}

@OptIn(DelicateCoroutinesApi::class)
internal fun buildMultipart(
    boundary: String,
    parts: List<PartData>
): ByteReadChannel =
    GlobalScope
        .writer {
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

private suspend fun WriterScope.append(
    str: String,
    charset: Charset = Charsets.UTF_8
) {
    channel.writeFully(str.toByteArray(charset))
}
