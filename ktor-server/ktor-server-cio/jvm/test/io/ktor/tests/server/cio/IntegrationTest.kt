/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.test.*

class IntegrationTest {
    private val pool = ForkJoinPool(4)

    private var port = 0
    private val server = CompletableDeferred<ServerSocketChannel>()
    private var handler: suspend (Request, ByteReadChannel, ByteWriteChannel) -> Unit = { r, _, o ->
        respond404(r, o)

        o.flushAndClose()
    }

    @BeforeTest
    fun setUp() {
        val dispatcher = pool.asCoroutineDispatcher()
        val (j, s) = testHttpServer(0, dispatcher, dispatcher) { request ->
            if (request.uri.toString() == "/do" && request.method == HttpMethod.Post) {
                handler(request, input, output)
            } else {
                respond404(request, output)
            }
        }

        s.invokeOnCompletion { t ->
            if (t != null) {
                server.completeExceptionally(t)
            } else server.complete(@OptIn(ExperimentalCoroutinesApi::class) s.getCompleted())
        }

        j.invokeOnCompletion {
            s.invokeOnCompletion { t ->
                if (t != null && !s.isCancelled) {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    s.getCompleted().close()
                }
            }
        }

        runBlocking {
            port = (s.await().localAddress as InetSocketAddress).port
        }
    }

    @AfterTest
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tearDown() {
        server.invokeOnCompletion { t ->
            if (t == null) {
                server.getCompleted().close()
                pool.shutdown()
            } else {
                pool.shutdown()
            }
        }
    }

    @Test
    fun testChunkedRequestResponse() {
        val url = URL("http://localhost:$port/do")

        handler = { r, input, o ->
            val rr = RequestResponseBuilder()
            try {
                rr.responseLine(r.version, 200, "OK")
                rr.headerLine("Connection", "close")
                rr.headerLine("Transfer-Encoding", "chunked")
                rr.emptyLine()
                o.writePacket(rr.build())
                o.flush()
            } finally {
                rr.release()
            }

            val chunked = encodeChunked(o, Dispatchers.Default)
            input.copyAndClose(chunked.channel)
            chunked.join()
        }

        val connection = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.allowUserInteraction = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.requestMethod = "POST"
            connection.setChunkedStreamingMode(0)

            connection.doInput = true
            connection.doOutput = true

            connection.outputStream.use {
                it.apply {
                    write("123\n".toByteArray())
                    flush()
                    write("456\n".toByteArray())
                    flush()
                    write("abc\n".toByteArray())
                    flush()
                }
            }

            val text = connection.inputStream.reader().readText()
            assertEquals("123\n456\nabc\n", text)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun respond404(request: Request, output: ByteWriteChannel) {
        val rr = RequestResponseBuilder()
        try {
            rr.responseLine(request.version, 404, "Not found")
            rr.headerLine("Connection", "close")
            rr.headerLine("Content-Length", "0")
            rr.emptyLine()
            output.writePacket(rr.build())
            output.flush()
        } finally {
            rr.release()
        }
    }
}
