/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

@Suppress("DEPRECATION")
class TestEngineMultipartTest {
    private val boundary = "***bbb***"
    private val contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    @Test
    fun testNonMultipart() {
        testMultiParts(
            {
                assertNull(it, "it should be no multipart data")
            },
            setup = {}
        )
    }

    @Test
    fun testMultiPartsPlainItemBinary() {
        val bytes = ByteArray(256) { it.toByte() }
        testMultiPartsFileItemBase(
            filename = "file.bin",
            provider = { ByteReadChannel(bytes) },
            extraFileAssertions = { file -> assertEquals(hex(bytes), hex(file.provider().readRemaining().readBytes())) }
        )
    }

    @Test
    fun testMultiPartsFileItemText() {
        val string = "file content with unicode 🌀 : здороваться : 여보세요 : 你好 : ñç"
        testMultiPartsFileItemBase(
            filename = "file.txt",
            provider = { ByteReadChannel(string.toByteArray()) },
            extraFileAssertions = { file -> assertEquals(string, file.provider().readRemaining().readText()) }
        )
    }

    @Test
    fun testMultiPartsFileItem() {
        val bytes = ByteArray(256) { it.toByte() }

        testMultiParts(
            {
                assertNotNull(it, "it should be multipart data")
                @Suppress("DEPRECATION")
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.name)
                assertEquals("file.bin", file.originalFileName)
                assertEquals(hex(bytes), hex(file.provider().readRemaining().readBytes()))

                file.dispose()
            },
            setup = {
                addHeader(HttpHeaders.ContentType, contentType.toString())
                bodyChannel = buildMultipart(
                    boundary,
                    listOf(
                        PartData.FileItem(
                            provider = { ByteReadChannel(bytes) },
                            dispose = {},
                            partHeaders = headersOf(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                    .withParameter(ContentDisposition.Parameters.FileName, "file.bin")
                                    .toString()
                            )
                        )
                    )
                )
            }
        )
    }

    @Test
    fun testMultiPartShouldFail() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                try {
                    @Suppress("DEPRECATION")
                    call.receiveMultipart().readAllParts()
                } catch (error: Throwable) {
                    fail("This pipeline shouldn't finish successfully")
                }
            }

            assertFailsWith<AssertionError> {
                handleRequest(HttpMethod.Post, "/")
            }
        }
    }

    private fun testMultiParts(asserts: suspend (MultiPartData?) -> Unit, setup: TestApplicationRequest.() -> Unit) {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                if (call.request.isMultipart()) {
                    asserts(call.receiveMultipart())
                } else {
                    asserts(null)
                }
            }

            handleRequest(HttpMethod.Post, "/", setup)
        }
    }

    private fun testMultiPartsFileItemBase(
        filename: String,
        provider: () -> ByteReadChannel,
        extraFileAssertions: suspend (file: PartData.FileItem) -> Unit
    ) {
        testMultiParts(
            {
                assertNotNull(it, "it should be multipart data")
                @Suppress("DEPRECATION")
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.name)
                assertEquals(filename, file.originalFileName)
                extraFileAssertions(file)

                file.dispose()
            },
            setup = {
                addHeader(HttpHeaders.ContentType, contentType.toString())
                bodyChannel = buildMultipart(
                    boundary,
                    listOf(
                        PartData.FileItem(
                            provider = provider,
                            dispose = {},
                            partHeaders = headersOf(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                    .withParameter(ContentDisposition.Parameters.FileName, filename)
                                    .toString()
                            )
                        )
                    )
                )
            }
        )
    }
}

@Suppress("DEPRECATION")
@OptIn(DelicateCoroutinesApi::class)
internal fun buildMultipart(
    boundary: String,
    parts: List<PartData>
): ByteReadChannel = GlobalScope.writer {
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
                        channel.writeFully(it.provider().readRemaining().readBytes())
                        ""
                    }

                    is PartData.BinaryItem -> {
                        channel.writeFully(it.provider().readBytes())
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
