/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class LoggingMockedTests {
    @Test
    fun testLogRequestWithException() = testWithEngine(MockEngine) {
        val testLogger = TestLogger(
            "REQUEST: ${URLBuilder.origin}",
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
            "REQUEST ${URLBuilder.origin} failed with exception: CustomError[BAD REQUEST]"
        )

        config {
            engine {
                addHandler {
                    throw CustomError("BAD REQUEST")
                }
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                client.get { url(port = DEFAULT_PORT) }
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLogResponseWithException() = testWithEngine(MockEngine) {
        val testLogger = TestLogger(
            "REQUEST: ${URLBuilder.origin}",
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
            "FROM: ${URLBuilder.origin}",
            "COMMON HEADERS",
            "+++RESPONSE ${URLBuilder.origin} failed with exception: CustomError[PARSE ERROR]",
            "BODY Content-Type: null",
            "BODY START",
            "Hello",
            "BODY END"
        )

        config {
            engine {
                addHandler {
                    respondOk("Hello")
                }
            }
            install("BadInterceptor") {
                responsePipeline.intercept(HttpResponsePipeline.Transform) {
                    throw CustomError("PARSE ERROR")
                }
            }

            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            if (PlatformUtils.IS_NATIVE) return@test

            var failed = false
            client.prepareGet { url(port = DEFAULT_PORT) }.execute {
                try {
                    it.body<String>()
                } catch (_: CustomError) {
                    failed = true
                }
            }

            assertTrue(failed, "Exception is missing.")
        }

        after {
            if (PlatformUtils.IS_NATIVE) return@after

            testLogger.verify()
        }
    }

    @Test
    fun testLogResponseWithExceptionSingle() = testWithEngine(MockEngine) {
        val testLogger = TestLogger(
            "REQUEST: ${URLBuilder.origin}",
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
            "FROM: ${URLBuilder.origin}",
            "COMMON HEADERS",
            "RESPONSE ${URLBuilder.origin} failed with exception: CustomError[PARSE ERROR]",
            "REQUEST ${URLBuilder.origin} failed with exception: CustomError[PARSE ERROR]"
        )

        config {
            engine {
                addHandler {
                    respondOk("Hello")
                }
            }
            install("BadInterceptor") {
                receivePipeline.intercept(HttpReceivePipeline.State) {
                    throw CustomError("PARSE ERROR")
                }
            }

            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                client.get { url(port = DEFAULT_PORT) }
            } catch (_: CustomError) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLoggingWithForm() = testWithEngine(MockEngine) {
        val testLogger = TestLogger(
            "REQUEST: http://localhost/",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "!!!-> Content-Type: multipart/form-data; " +
                "boundary=27e7dfaa-451f2057-3dabbd0c2b3cae572a4935af6a57b2d4bb335c34480373360863",
            "!!! BODY Content-Type: multipart/form-data; " +
                "boundary=41a55fb5-2ae7bc4b-5b124e524086ca1e-6879a99a75b8a0a028a6a7d7-63d38251-5",
            "BODY START",
            "!!!--41a55fb5-2ae7bc4b-5b124e524086ca1e-6879a99a75b8a0a028a6a7d7-63d38251-5",
            """Content-Disposition: form-data; name=file; file; name=""; filename=""""",
            "",
            "Hello",
            """!!!--41a55fb5-2ae7bc4b-5b124e524086ca1e-6879a99a75b8a0a028a6a7d7-63d38251-5--""",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://localhost/",
            "COMMON HEADERS",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END"
        )

        config {
            engine {
                addHandler {
                    val body = it.body.toByteReadPacket().readText()
                    assertTrue { body.contains("Hello") }
                    respondOk()
                }
            }

            Logging {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            val input = buildPacket { writeText("Hello") }
            client.submitFormWithBinaryData(
                "http://localhost/",
                formData {
                    appendInput(
                        "file",
                        headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "")
                                .withParameter(ContentDisposition.Parameters.FileName, "")
                                .toString()
                        )
                    ) { input }
                }
            ).body<String>()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testFilterRequest() = testWithEngine(MockEngine) {
        val testLogger = TestLogger(
            "REQUEST: http://somewhere/filtered_path",
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
            "FROM: http://somewhere/filtered_path",
            "COMMON HEADERS",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END"
        )

        config {
            engine {
                addHandler { respondOk() }
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
                filter { it.url.encodedPath == "/filtered_path" }
            }
        }

        test { client ->
            client.get(urlString = "http://somewhere/filtered_path")
            client.get(urlString = "http://somewhere/not_filtered_path")
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testSanitizeHeaders() = testWithEngine(MockEngine) {
        val testLogger = TestLogger {
            line("REQUEST: http://localhost/")
            line("METHOD: HttpMethod(value=GET)")
            line("COMMON HEADERS")
            line("-> Accept: */*")
            line("-> Accept-Charset: UTF-8")
            line("-> Authorization: <secret>")
            line("-> Sanitized: ***")
            line("CONTENT HEADERS")
            line("-> Content-Length: 0")
            line("RESPONSE: 200 OK")
            line("METHOD: HttpMethod(value=GET)")
            line("FROM: http://localhost/")
            line("COMMON HEADERS")
            line("-> Sanitized: ***")
        }
        config {
            engine {
                addHandler { respond("OK", headers = headersOf("Sanitized", "response value")) }
            }
            install(Logging) {
                logger = testLogger
                level = LogLevel.HEADERS
                sanitizeHeader("<secret>") { it == HttpHeaders.Authorization }
                sanitizeHeader { it == "Sanitized" }
                sanitizeHeader { it == HttpHeaders.ContentType }
            }
        }

        test { client ->
            client.get("http://localhost/") {
                header(HttpHeaders.Authorization, "password")
                header("Sanitized", "value")
            }.body<String>()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testCanStream() = testWithEngine(MockEngine) {
        val channel = ByteChannel(autoFlush = true)
        config {
            engine {
                addHandler {
                    respond(
                        content = channel,
                        status = HttpStatusCode.OK
                    )
                }
            }
            install(Logging) {
                level = LogLevel.BODY
                logger = Logger.DEFAULT
            }
        }
        test { client ->
            val content = channelFlow {
                launch {
                    client.preparePost("/").execute {
                        val ch = it.bodyAsChannel()
                        while (!ch.isClosedForRead) {
                            ch.awaitContent()
                            send(ch.readUTF8Line())
                        }
                    }
                }
            }

            channel.writeStringUtf8("Hello world!\n")

            withTimeout(5_000) { // the bug will cause this to timeout
                content.collect {
                    channel.close()
                }
            }
        }
    }
}
