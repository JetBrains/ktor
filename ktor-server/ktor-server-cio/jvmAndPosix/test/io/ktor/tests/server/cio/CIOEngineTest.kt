// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.suites.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class CIOHttpServerTest : HttpServerCommonTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {

    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Test
    fun testChunkedResponse() = runTest {
        createAndStartServer {
            get("/") {
                val byteStream = ByteChannel(autoFlush = true)
                byteStream.writeStringUtf8("test")
                byteStream.close(null)
                call.respond(object : OutgoingContent.ReadChannelContent() {
                    override val status: HttpStatusCode = HttpStatusCode.OK
                    override val headers: Headers = Headers.Empty
                    override fun readFrom() = byteStream
                })
            }
        }

        withUrl("/") {
            assertEquals("test", bodyAsText())
        }
    }

    @Test
    fun testExpectedContinue() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()
            val body = "Hello world"

            writePostHeaders(writeChannel, body.length)
            val continueResponse = readChannel.readUTF8Line()
            assertEquals("HTTP/1.1 100 Continue", continueResponse)

            writePostBody(writeChannel, body)
            val response = readAvailable(readChannel)
            assertTrue(response.contains("HTTP/1.1 200 OK"))
            assertTrue(response.contains(body))
        }
    }

    @Test
    fun testExpectedContinueRespondBeforeReadingBody() = runTest {
        createAndStartServer {
            post("/") {
                val length = call.request.headers[HttpHeaders.ContentLength]?.toInt() ?: 0
                if (length > 5) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val longBody = "Hello world"
            writePostHeaders(writeChannel, longBody.length)
            val badRequestResponse = readAvailable(readChannel)
            assertTrue(badRequestResponse.contains("HTTP/1.1 400 Bad Request"))
        }
    }

    @Test
    fun testExpectedContinueExpectationFailed() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val longBody = "Hello world"
            writePostHeaders(writeChannel, longBody.length, expectedHeader = "invalid-100-continue")
            val expectationFailedResponse = readAvailable(readChannel)
            assertTrue(expectationFailedResponse.contains("HTTP/1.1 417 Expectation Failed"))
        }
    }

    @Test
    fun testExpectedContinueConnection() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respond(body)
            }
            post("/check-length") {
                val length = call.request.headers[HttpHeaders.ContentLength]?.toInt() ?: 0
                if (length == 0) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                call.respondText("ok")
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            writePostHeaders(writeChannel, path = "/check-length")
            val response = readAvailable(readChannel)
            assertTrue(response.contains("Connection: close"))
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            writePostHeaders(writeChannel, expectedHeader = "invalid")
            val response = readAvailable(readChannel)
            assertTrue(response.contains("Connection: close"))
        }
    }

    @Test
    fun testExpectedIgnoreHTTP1_0() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respond(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val body = "Hello world"
            writePostHeaders(writeChannel, body.length, httpVersion = "HTTP/1.0")
            writePostBody(writeChannel, body)
            val response = readAvailable(readChannel)
            assertFalse(response.contains("100 Continue"))
        }
    }

    private suspend fun readAvailable(channel: ByteReadChannel): String {
        val buffer = ByteArray(1024)
        val length = channel.readAvailable(buffer)
        return buffer.decodeToString(0, 0 + length)
    }

    private suspend fun withClientSocket(block: suspend Socket.() -> Unit) {
        SelectorManager().use {
            aSocket(it).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                block(socket)
            }
        }
    }

    private suspend fun writePostHeaders(
        channel: ByteWriteChannel,
        length: Int = 0,
        path: String = "/",
        expectedHeader: String = "100-continue",
        httpVersion: String = "HTTP/1.1"
    ) {
        channel.apply {
            writeStringUtf8("POST $path $httpVersion\r\n")
            writeStringUtf8("Host: $TEST_SERVER_HOST\r\n")
            writeStringUtf8("Content-Type: text/plain\r\n")
            writeStringUtf8("Content-Length: $length\r\n")
            writeStringUtf8("Expect: $expectedHeader\r\n")
            writeStringUtf8("Connection: close\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private suspend fun writePostBody(channel: ByteWriteChannel, body: String) {
        channel.apply {
            writeStringUtf8("$body\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
    }
}
