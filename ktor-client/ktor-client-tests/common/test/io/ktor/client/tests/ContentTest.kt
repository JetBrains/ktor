/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

private val testSize = listOf(
    0,
    1, // small edge cases
    4 * 1024 - 1,
    4 * 1024,
    4 * 1024 + 1, // ByteChannel edge cases
    10 * 4 * 1024, // 4 chunks
    10 * 4 * (1024 + 8), // 4 chunks
    8 * 1024 * 1024 // big
)

val testStrings = testSize.map {
    makeString(it)
}

val testArrays = testSize.map {
    makeArray(it)
}

class ContentTest : ClientLoader(5 * 60) {

    @Test
    fun testGetFormData() = clientTests {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "page" to listOf("10")
            )

            val response = client.submitForm(
                "$TEST_SERVER/content/news",
                encodeInQuery = true,
                formParameters = form
            ).body<String>()

            assertEquals("100", response)
        }
    }

    @Test
    fun testByteArray() = clientTests {
        test { client ->
            testArrays.forEach { content ->
                val response = client.echo<ByteArray>(content)

                assertArrayEquals(
                    "Test fail with size: ${content.size}. Actual size: ${response.size}",
                    content,
                    response
                )
            }
        }
    }

    @Test
    fun testByteReadChannel() = clientTests {
        config {
            install(HttpTimeout) {
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }
        test { client ->
            testArrays.forEach { content ->
                val responseData = client.echo<ByteReadChannel>(content)
                val data = responseData.readRemaining().readBytes()
                assertArrayEquals(
                    "Test fail with size: ${content.size}, actual size: ${data.size}",
                    content,
                    data
                )
            }
        }
    }

    @Test
    fun testSendByteReadChannel() = clientTests(listOf("Js")) {
        config {
            install(HttpTimeout) {
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }
        test { client ->
            testArrays.forEach { content ->
                val data = client.post("$TEST_SERVER/content/echo") {
                    setBody(ByteReadChannel(content))
                }.body<ByteArray>()
                assertArrayEquals(
                    "Test fail with size: ${content.size}, actual size: ${data.size}",
                    content,
                    data
                )
            }
        }
    }

    @Test
    fun testSendByteWriteChannel() = clientTests(listOf("Js")) {
        config {
            install(HttpTimeout) {
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }
        test { client ->
            testArrays.forEach { content ->
                val data = client.post("$TEST_SERVER/content/echo") {
                    setBody(
                        ChannelWriterContent(
                            body = { writeFully(content) },
                            contentType = ContentType.Application.OctetStream,
                        )
                    )
                }.body<ByteArray>()
                assertArrayEquals(
                    "Test fail with size: ${content.size}, actual size: ${data.size}",
                    content,
                    data
                )
            }
        }
    }

    @Test
    fun testString() = clientTests(listOf("Darwin", "CIO", "DarwinLegacy")) {
        test { client ->
            testStrings.forEach { content ->
                val requestWithBody = client.echo<String>(content)
                assertArrayEquals(
                    "Test fail with size: ${content.length}",
                    content.toByteArray(),
                    requestWithBody.toByteArray()
                )
            }
        }
    }

    @Test
    fun testEmptyContent() = clientTests {
        val size = 0
        val content = makeString(size)
        repeatCount = 200
        test { client ->
            val response = client.echo<String>(TextContent(content, ContentType.Text.Plain))

            assertArrayEquals(
                "Test fail with size: $size",
                content.toByteArray(),
                response.toByteArray()
            )
        }
    }

    @Test
    fun testTextContent() = clientTests(listOf("Darwin", "CIO", "DarwinLegacy")) {
        test { client ->
            testStrings.forEach { content ->
                val response = client.echo<String>(TextContent(content, ContentType.Text.Plain))

                assertArrayEquals(
                    "Test fail with size: ${content.length}",
                    content.toByteArray(),
                    response.toByteArray()
                )
            }
        }
    }

    @Test
    fun testByteArrayContent() = clientTests {
        test { client ->
            testArrays.forEach { content ->
                val response = client.echo<ByteArray>(ByteArrayContent(content))

                assertArrayEquals("Test fail with size: ${content.size}", content, response)
            }
        }
    }

    @Test
    fun testPostFormData() = clientTests {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "token" to listOf("abcdefg")
            )

            val response = client.submitForm("$TEST_SERVER/content/sign", formParameters = form).body<String>()
            assertEquals("success", response)
        }
    }

    @Test
    @Ignore
    fun testMultipartFormData() = clientTests {
        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeText("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("file2", "urlencoded_name2.jpg", ContentType.Application.OctetStream) {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
                append("world", true)
                append("engines[]", listOf("Jvm", "Native"))
            }
        }

        test { client ->
            val response = client.submitFormWithBinaryData(
                "$TEST_SERVER/content/upload",
                formData = data()
            ).body<String>()
            val contentString = data().makeString()
            assertEquals(contentString, response)
        }
    }

    @Test
    fun testLargeFormWithBinaryData() = clientTests {
        val formData = {
            formData {
                append(
                    key = "image",
                    value = ByteArray(100 * 1024) { 0 },
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=image.jpeg")
                    },
                )
            }
        }

        test { client ->
            client.submitFormWithBinaryData("$TEST_SERVER/content/upload", formData()).body<ByteArray>()
        }
    }

    @Test
    fun testMultipartWithByteReadChannel() = clientTests {
        test { client ->
            val response = client.submitFormWithBinaryData(
                "$TEST_SERVER/echo",
                formData = formData {
                    append("channel", ChannelProvider { ByteReadChannel("from channel") })
                }
            ).body<String>()

            assertContains(response, "Content-Disposition: form-data; name=channel")
            assertContains(response, "from channel")
        }
    }

    @Test
    fun testFormDataWithContentLength() = clientTests {
        test { client ->
            client.submitForm {
                url("$TEST_SERVER/content/file-upload")
                method = HttpMethod.Put

                setBody(
                    MultiPartFormDataContent(
                        formData {
                            appendInput(
                                "image",
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpg")
                                    append(HttpHeaders.ContentDisposition, "filename=hello.jpg")
                                },
                                size = 4
                            ) { buildPacket { writeInt(42) } }
                        }
                    )
                )
            }.body<Unit>()
        }
    }

    @Test
    fun testJsonPostWithEmptyBody() = clientTests {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            val response = client.post("$TEST_SERVER/echo") {
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("", response)
        }
    }

    @Test
    fun testPostWithEmptyBody() = clientTests {
        config {
        }

        test { client ->
            val response = client.post("$TEST_SERVER/echo") {
                setBody(EmptyContent)
            }.body<String>()

            assertEquals("", response)
        }
    }

    @Test
    @Ignore
    fun testDownloadStreamChannelWithCancel() = clientTests {
        test { client ->
            val content = client.get("$TEST_SERVER/content/stream").body<ByteReadChannel>()
            content.cancel()
        }
    }

    @Test
    fun testDownloadStreamResponseWithClose() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
            }
        }
    }

    // NSUrlSession buffers first 512 bytes
    @Test
    fun testDownloadStream() = clientTests(listOf("Darwin", "DarwinLegacy")) {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream?delay=100").execute {
                val channel = it.bodyAsChannel()
                var count = 0
                while (count < 5) {
                    channel.readInt()
                    count++
                }
            }
        }
    }

    @Test
    fun testDownloadStreamResponseWithCancel() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
                it.cancel()
            }
        }
    }

    @Test
    fun testDownloadStreamArrayWithTimeout() = clientTests {
        test { client ->
            val result: ByteArray? = withTimeoutOrNull(100) {
                client.get("$TEST_SERVER/content/stream").body<ByteArray>()
            }

            assertNull(result)
        }
    }

    private suspend inline fun <reified Response : Any> HttpClient.echo(
        body: Any
    ): Response = post("$TEST_SERVER/content/echo") {
        setBody(body)
    }.body()
}
