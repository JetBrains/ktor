/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.http.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.time.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*

class CIOHttpClientTest {
    @Test
    fun testHttpConnection() = runBlocking {
        val portSync = ArrayBlockingQueue<Int>(1)
        val headersSync = ArrayBlockingQueue<Map<String, String>>(1)
        val receivedContentSync = ArrayBlockingQueue<String>(1)

        val th = thread {
            ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).use { server ->
                portSync.add(server.localPort)

                server.accept()!!.use { client ->
                    val reader = client.inputStream.bufferedReader()

                    val headers = reader.lineSequence().takeWhile { it.isNotBlank() }
                        .associateBy({ it.substringBefore(":", "") }, { it.substringAfter(":").trimStart() })
                    headersSync.add(headers)

                    val requestContentBuffer = CharArray(headers[HttpHeaders.ContentLength]!!.toInt())
                    var read = 0
                    while (read < requestContentBuffer.size) {
                        val rc = reader.read(requestContentBuffer, read, requestContentBuffer.size - read)
                        require(rc != -1) { "premature end of stream" }

                        read += rc
                    }

                    val requestContent = String(requestContentBuffer)
                    receivedContentSync.add(requestContent)

                    client.outputStream.writer().apply {
                        write(
                            """
                            HTTP/1.1 200 OK
                            Server: test
                            Date: ${LocalDateTime.now().toHttpDateString()}
                            Connection: close
                            """.trimIndent()
                                .lines()
                                .joinToString("\r\n", postfix = "\r\n\r\nok")
                        )
                        flush()
                    }
                }
            }
        }

        val port = portSync.take()
        val client = HttpClient(CIO)
        val response = client.request("http://127.0.0.1:$port/") {
            method = HttpMethod.Post
            url.encodedPath = "/url"
            header("header", "value")
            setBody("request-body")
        }

        try {
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("test", response.headers[HttpHeaders.Server])
            assertEquals("ok", response.bodyAsText())

            val receivedHeaders = headersSync.take()
            assertEquals("value", receivedHeaders["header"])
            assertEquals("POST /url HTTP/1.1", receivedHeaders[""])
            assertEquals("127.0.0.1:$port", receivedHeaders[HttpHeaders.Host])

            assertEquals("request-body", receivedContentSync.take())
        } finally {
            client.close()
            th.join()
        }
    }

    @Test
    fun testHttpConnectionChunked() = runBlocking {
        val portSync = CompletableDeferred<Int>()
        val headersSync = CompletableDeferred<Map<String, String>>()
        val receivedContentSync = CompletableDeferred<String>()

        val th = thread {
            ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).use { server ->
                portSync.complete(server.localPort)

                server.accept()!!.use { client ->
                    val reader = client.inputStream.bufferedReader()

                    val headers = reader.lineSequence().takeWhile { it.isNotBlank() }
                        .associateBy({ it.substringBefore(":", "") }, { it.substringAfter(":").trimStart() })
                    headersSync.complete(headers)

                    assertEquals("chunked", headers[HttpHeaders.TransferEncoding])

                    val requestContentBuffer = StringBuilder()
                    val chunkBuffer = CharArray(512)

                    while (true) {
                        val line = reader.readLine()?.trim() ?: break
                        val chunkSize = line.toInt(16)

                        var copied = 0
                        while (copied < chunkSize) {
                            val rc = reader.read(chunkBuffer, 0, minOf(512, chunkSize - copied))
                            if (rc == -1) throw EOFException("Premature end of stream")
                            requestContentBuffer.appendRange(chunkBuffer, 0, rc)
                            copied += rc
                        }

                        assertEquals("", reader.readLine())

                        if (chunkSize == 0) break
                    }

                    val requestContent = requestContentBuffer.toString()
                    receivedContentSync.complete(requestContent)

                    client.outputStream.writer().apply {
                        write(
                            """
                            HTTP/1.1 200 OK
                            Server: test
                            Date: ${LocalDateTime.now().toHttpDateString()}
                            Transfer-Encoding: chunked
        
                            2
                            ok
                            0
                            """.trimIndent()
                                .lines()
                                .joinToString("\r\n", postfix = "\r\n\r\n")
                        )
                        flush()
                    }
                }
            }
        }

        val port = portSync.await()

        val client = HttpClient(CIO)
        val response = client.request("http://127.0.0.1:$port/") {
            method = HttpMethod.Post
            url.encodedPath = "/url"
            header("header", "value")
            setBody(
                TextContent("request-body", ContentType.Text.Plain).wrapHeaders { hh ->
                    HeadersBuilder().apply {
                        appendAll(hh)
                        append(HttpHeaders.TransferEncoding, "chunked")
                    }.build()
                }
            )
        }

        try {
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("test", response.headers[HttpHeaders.Server])
            assertEquals("ok", response.bodyAsText())

            val receivedHeaders = headersSync.await()
            assertEquals("value", receivedHeaders["header"])
            assertEquals("POST /url HTTP/1.1", receivedHeaders[""])
            assertEquals("127.0.0.1:$port", receivedHeaders[HttpHeaders.Host])

            assertEquals("request-body", receivedContentSync.await())
        } finally {
            client.close()
            th.join()
        }
    }
}
