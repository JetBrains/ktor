/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

@OptIn(DelicateCoroutinesApi::class)
class LoggingTest : ClientLoader() {
    private val content = "Response data"
    private val serverPort = 8080

    @Test
    fun testDownloadWithNoneLogLevel() = clientTests {
        val testLogger = TestLogger()
        config {
            install(Logging) {
                level = LogLevel.NONE
                logger = testLogger
            }
        }

        test { client ->
            val size = 4 * 1024 * 1024
            client.prepareGet("$TEST_SERVER/bytes?size=$size").execute {
                assertEquals(size, it.readBytes().size)
            }
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLoggingLevelBody() = clientTests(listOf("native:CIO")) {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging",
            "METHOD: HttpMethod(value=GET)",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "home page",
            "BODY END"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.BODY)
    }

    @Test
    fun testLogLevelAll() = clientTests(listOf("native:CIO")) {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "???-> Connection: close",
            "-> Content-Length: 9",
            "-> Content-Type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "home page",
            "BODY END"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.ALL)
    }

    @Test
    fun testLogLevelHeaders() = clientTests {
        val logger = TestLogger {
            line("REQUEST: http://localhost:8080/logging")
            line("METHOD: HttpMethod(value=GET)")
            line("COMMON HEADERS")
            line("-> Accept: */*")
            line("-> Accept-Charset: UTF-8")
            line("CONTENT HEADERS")
            line("-> Content-Length: 0")
            line("RESPONSE: 200 OK")
            line("METHOD: HttpMethod(value=GET)")
            line("FROM: http://localhost:8080/logging")
            line("COMMON HEADERS")
            optional("-> Connection: close")
            optional("-> Connection: keep-alive")
            line("-> Content-Length: 9")
            line("-> Content-Type: text/plain; charset=UTF-8")
        }
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.HEADERS)
    }

    @Test
    fun testLogLevelInfo() = clientTests {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging",
            "METHOD: HttpMethod(value=GET)",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.INFO)
    }

    @Test
    fun testLogLevelNone() = clientTests {
        val logger = TestLogger()
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.NONE)
    }

    @Test
    fun testLogPostBody() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://localhost:8080/logging",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 13",
            "-> Content-Type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            content,
            "BODY END",
            "RESPONSE: 201 Created",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://localhost:8080/logging",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> content-length: 1",
            "-> content-type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "/",
            "BODY END"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            client.prepareRequest {
                method = HttpMethod.Post

                url {
                    encodedPath = "/logging"
                    port = serverPort
                }

                setBody(content)
            }.execute {
                it.bodyAsText()
            }
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLogPostMalformedUtf8Body() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://localhost:8080/logging/non-utf",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 2",
            "-> Content-Type: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "�o",
            "BODY END",
            "RESPONSE: 201 Created",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://localhost:8080/logging/non-utf",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> Content-Length: 2",
            "-> Content-Type: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "�o",
            "BODY END"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            client.prepareRequest {
                method = HttpMethod.Post

                url {
                    encodedPath = "/logging/non-utf"
                    port = serverPort
                }

                setBody(byteArrayOf(-77, 111))
            }.execute {
                it.readBytes()
            }
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testRequestAndResponseBody() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 4",
            "-> Content-Type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "test",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://127.0.0.1:8080/content/echo",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> content-length: 4",
            "BODY Content-Type: null",
            "BODY START",
            "test",
            "BODY END"
        )

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request {
                method = HttpMethod.Post
                setBody("test")
                url("$TEST_SERVER/content/echo")
            }.body<ByteReadChannel>()
            assertNotNull(response)
            assertEquals("test", response.readRemaining().readText())
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testRequestContentTypeInLog() = clientTests(listOf("Darwin", "native:CIO", "DarwinLegacy")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 4",
            "-> Content-Type: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "test",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://127.0.0.1:8080/content/echo",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "???-> connection: close",
            "-> Content-Length: 4",
            "BODY Content-Type: null",
            "BODY START",
            "test",
            "BODY END"
        )

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request {
                method = HttpMethod.Post
                setBody("test")
                contentType(ContentType.Application.OctetStream)
                url("$TEST_SERVER/content/echo")
            }.body<ByteReadChannel>()

            assertNotNull(response)
            response.discard()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLoggingWithCompression() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/compression/deflate",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "-> Accept-Encoding: gzip,deflate,identity",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://127.0.0.1:8080/compression/deflate",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "???-> connection: close",
            "-> Content-Type: text/plain; charset=UTF-8",
            "-> Transfer-Encoding: chunked",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "???[response body omitted]",
            "???Compressed response!", // Curl engine
            "BODY END"
        )
        config {
            ContentEncoding()
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.prepareGet {
                method = HttpMethod.Get
                url("$TEST_SERVER/compression/deflate")
            }.execute()
            assertEquals("Compressed response!", response.body<String>())
        }
        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLoggingWithStreaming() = clientTests {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)"
        )
        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val body = ByteChannel()
            GlobalScope.launch {
                body.writeFully(ByteArray(16 * 1024) { 1 })
                body.close()
            }

            val response = client.prepareRequest {
                method = HttpMethod.Post
                url("$TEST_SERVER/content/echo")
                setBody(body)
            }.body<ByteReadChannel>()
            response.discard()
        }
    }

    @Test
    fun testBodyLoggingKeepsContent() = clientTests {
        val logs = mutableListOf<String>()
        val testLogger = object : Logger {
            override fun log(message: String) {
                logs.add(message)
            }
        }

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.post("$TEST_SERVER/content/echo") {
                setBody(MultiPartFormDataContent(formData { append("file", "123") }))
            }

            assertNotNull(response.body<String>())
            val request = response.request
            val contentLength = request.content.contentLength!!
            val contentType = request.content.contentType!!

            assertTrue(contentType.contentType == "multipart")
            assertTrue(contentType.contentSubtype == "form-data")
            assertTrue(contentType.parameters.any { it.name == "boundary" })
            assertTrue(logs.any { it.contains("Content-Type: $contentType") })
            assertTrue(logs.any { it.contains("Content-Length: $contentLength") })
        }
    }

    private fun TestClientBuilder<*>.checkLog(
        testLogger: TestLogger,
        requestMethod: HttpMethod,
        path: String,
        body: String?,
        logLevel: LogLevel
    ) {
        config {
            install(Logging) {
                logger = testLogger
                level = logLevel
            }
        }

        test { client ->
            client.request {
                method = requestMethod

                url {
                    encodedPath = if (path.isEmpty()) "/logging" else "/logging/$path"
                    port = serverPort
                }

                body?.let { setBody(body) }
            }.body<String>()
        }

        after {
            testLogger.verify()
        }
    }

    @Serializable
    data class User(val name: String)

    @Test
    fun testLogPostBodyWithJson() = clientTests {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: application/json",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 15",
            "-> Content-Type: application/json",
            "BODY Content-Type: application/json",
            "BODY START",
            "{\"name\":\"Ktor\"}",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://127.0.0.1:8080/content/echo",
            "COMMON HEADERS",
            "???-> connection: keep-alive",
            "???-> connection: close",
            "-> content-length: 15",
            "BODY Content-Type: null",
            "BODY START",
            "{\"name\":\"Ktor\"}",
            "BODY END"
        )

        config {
            install(ContentNegotiation) { json() }

            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request {
                method = HttpMethod.Post
                setBody(User("Ktor"))
                contentType(ContentType.Application.Json)
                url("$TEST_SERVER/content/echo")
            }.body<ByteReadChannel>()

            assertNotNull(response)
        }

        after {
            testLogger.verify()
        }
    }
}
