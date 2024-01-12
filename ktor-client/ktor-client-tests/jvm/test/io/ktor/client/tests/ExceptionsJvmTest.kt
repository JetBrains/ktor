/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import org.apache.http.*
import java.net.*
import kotlin.test.*

@Suppress("BlockingMethodInNonBlockingContext", "ControlFlowWithEmptyBody")
class ExceptionsJvmTest {

    @Test
    fun testConnectionCloseException(): Unit = runBlocking {
        val client = HttpClient(Apache)

        client.use {
            assertFailsWith<ConnectionClosedException> {
                it.get("$TCP_SERVER/errors/few-bytes")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testConnectionClosedDuringRequest(): Unit = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort

        GlobalScope.launch {
            repeat(100) {
                val client = server.accept()
                val input = client.inputStream.bufferedReader()
                val output = client.outputStream.writer()

                while (input.readLine().isNotEmpty()) {
                }

                output.write("HTTP/1.1 200 Connection established\r\n")
                output.write("Content-Length: 100\r\n\r\n")
                output.write("Content")
                output.flush()

                output.close()
                client.close()
            }
            server.close()
        }

        HttpClient(Apache).use { client ->
            repeat(100) {
                assertFailsWith<ConnectionClosedException> {
                    client.get("http://127.0.0.1:$port")
                }
            }
        }
    }
}
