/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.io.use
import kotlin.test.*

class NettyReadRequestTimeoutTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
        private const val SUCCESS_RESPONSE = "HTTP/1.1 200 OK"
        private const val REQUEST_TIMEOUT_RESPONSE = "HTTP/1.1 408 Request Timeout"
        private const val BODY = "Hello world"
    }

    private fun getServer(timeout: Int?) = embeddedServer(
        Netty,
        module = {
            routing {
                get("/echo") {
                    call.respondText(call.receiveText())
                }
            }
        },
        configure = {
            connector {
                this.port = this@NettyReadRequestTimeoutTest.port
                this.host = TEST_SERVER_HOST
            }
            if (timeout != null) {
                requestReadTimeoutSeconds = timeout
            }
        }
    )

    private fun requestTimeout(timeout: Int?) = requestTimeoutTest(timeout) { writer, reader ->
        performAndCheckSuccessRequest("/echo", writer, reader)
    }

    @Test
    fun `no request timeout`() = requestTimeout(timeout = null)

    @Test
    fun `big request timeout`() = requestTimeout(timeout = Int.MAX_VALUE)

    @Test
    fun `request with readTimeout`() = requestTimeoutTest(timeout = 1) { writer, reader ->
        performAndCheckRequestTimeoutRequest("/echo", timeout = 1000, writer, reader)
    }

    @Test
    fun `success request and readTimeout request`() = requestTimeoutTest(timeout = 1) { writer, reader ->
        performAndCheckSuccessRequest("/echo", writer, reader)

        performAndCheckRequestTimeoutRequest("/echo", timeout = 1000, writer, reader)
    }

    @Test
    fun `test with ktor HttpClient`() = requestTimeoutTest(timeout = 1) { _, _ ->
        val client = HttpClient()
        client.performAndCheckRequestWithTimeout()
    }

    @Test
    fun `parallel requests`() = requestTimeoutTest(timeout = 1) { _, _ ->
        val client = HttpClient()
        client.performAndCheckRequestWithTimeout()
        client.performAndCheckRequestWithoutTimeout()
    }

    @Test
    fun `parallel timeout requests`() = requestTimeoutTest(timeout = 1) { _, _ ->
        val client = HttpClient()
        client.performAndCheckRequestWithTimeout()
        client.performAndCheckRequestWithTimeout()
    }

    private suspend fun HttpClient.performAndCheckRequestWithTimeout() {
        get {
            url(host = TEST_SERVER_HOST, path = "/echo", port = this@NettyReadRequestTimeoutTest.port)
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    delay(1100)
                    channel.writeFully("Hello world".toByteArray())
                }
            })
        }.apply {
            assertEquals(HttpStatusCode.RequestTimeout, status)
        }
    }

    private suspend fun HttpClient.performAndCheckRequestWithoutTimeout() {
        get {
            url(host = TEST_SERVER_HOST, path = "/echo", port = this@NettyReadRequestTimeoutTest.port)
            setBody("Hello world")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private suspend fun performAndCheckSuccessRequest(
        path: String,
        writeChannel: ByteWriteChannel,
        readChannel: ByteReadChannel
    ) {
        writeChannel.writeHeaders(path)
        writeChannel.writeBody()

        val response = readAvailable(readChannel)
        assertTrue(response.contains(SUCCESS_RESPONSE))
        assertTrue(response.contains(BODY))
        assertFalse(readChannel.isClosedForRead)
    }

    private suspend fun performAndCheckRequestTimeoutRequest(
        path: String,
        timeout: Long = 1000,
        writeChannel: ByteWriteChannel,
        readChannel: ByteReadChannel
    ) {
        writeChannel.writeHeaders(path)
        delay(timeout)

        val response = readAvailable(readChannel)
        assertTrue(response.contains(REQUEST_TIMEOUT_RESPONSE))
        // wait for channel to close
        delay(1000)
        assertTrue(readChannel.isClosedForRead)
    }

    private fun requestTimeoutTest(
        timeout: Int? = null,
        block: suspend (ByteWriteChannel, ByteReadChannel) -> Unit
    ) = runTest {
        val server = getServer(timeout)
        server.start(wait = false)

        SelectorManager().use {
            aSocket(it).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                val writeChannel = socket.openWriteChannel()
                val readChannel = socket.openReadChannel()
                block(writeChannel, readChannel)
            }
        }

        server.stop()
    }

    private suspend fun ByteWriteChannel.writeHeaders(path: String) {
        writeStringUtf8("GET $path HTTP/1.1\r\n")
        writeStringUtf8("Host: $TEST_SERVER_HOST\r\n")
        writeStringUtf8("Content-Type: text/plain\r\n")
        writeStringUtf8("Content-Length: ${BODY.length}\r\n")
        writeStringUtf8("\r\n")
        flush()
    }

    private suspend fun ByteWriteChannel.writeBody() {
        writeStringUtf8("$BODY\r\n")
        writeStringUtf8("\r\n")
        flush()
    }

    private suspend fun readAvailable(channel: ByteReadChannel): String {
        val buffer = ByteArray(1024)
        val length = channel.readAvailable(buffer)
        return buffer.decodeToString(0, 0 + length)
    }
}
