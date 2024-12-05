/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.junit.coroutines.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.test.*

@CoroutinesTimeout(5 * 60 * 1000)
class ConnectErrorsTest {

    private val serverSocket = ServerSocket(0, 1)

    @AfterTest
    fun teardown() {
        serverSocket.close()
    }

    @Test
    fun testConnectAfterConnectionErrors(): Unit = runBlocking {
        val client = HttpClient(CIO) {
            engine {
                maxConnectionsCount = 1
                endpoint.connectTimeout = SOCKET_CONNECT_TIMEOUT
                endpoint.connectAttempts = 3
            }
        }

        client.use {
            serverSocket.close()

            repeat(5) {
                try {
                    client.request("http://localhost:${serverSocket.localPort}/")
                    fail("Shouldn't reach here")
                } catch (_: ConnectException) {
                }
            }

            ServerSocket(serverSocket.localPort).use { newServer ->
                val thread = thread {
                    try {
                        newServer.accept().use { client ->
                            client.getOutputStream().let { out ->
                                out.write(
                                    "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 2\r\n\r\nOK".toByteArray()
                                )
                                out.flush()
                            }
                            client.getInputStream().readBytes()
                        }
                    } catch (ignore: SocketException) {
                    }
                }
                withTimeout(10000L) {
                    assertEquals("OK", client.get("http://localhost:${serverSocket.localPort}/").body())
                }
                thread.join()
            }
        }
    }

    @Test
    fun testResponseWithNoLengthChunkedAndConnectionClosedWithHttp10(): Unit = runBlocking {
        val client = HttpClient(CIO)

        client.use {
            serverSocket.close()

            ServerSocket(serverSocket.localPort).use { newServer ->
                val thread = thread {
                    try {
                        newServer.accept().use { client ->
                            client.getOutputStream().let { out ->
                                out.write("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n\r\nOK".toByteArray())
                                out.flush()
                                out.close()
                            }
                            client.getInputStream().readBytes()
                        }
                    } catch (_: Exception) { }
                }
                assertEquals("OK", client.get("http://localhost:${serverSocket.localPort}/").body())
                thread.join()
            }
        }
    }

    @Test
    fun testResponseErrorWithNoLengthChunkedAndConnectionClosedWithHttp11(): Unit = runBlocking {
        val client = HttpClient(CIO)

        client.use {
            serverSocket.close()

            ServerSocket(serverSocket.localPort).use { newServer ->
                val thread = thread {
                    try {
                        newServer.accept().use { client ->
                            client.getOutputStream().let { out ->
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK".toByteArray())
                                out.flush()
                                out.close()
                            }
                            client.getInputStream().readBytes()
                        }
                    } catch (ignore: SocketException) {
                    }
                }
                assertFails { client.get("http://localhost:${serverSocket.localPort}/") }
                thread.join()
            }
        }
    }

    @Test
    fun testResponseErrorWithInvalidChunkException(): Unit = runBlocking {
        val client = HttpClient(CIO) {
            install(HttpRequestRetry) {
                retryOnException(maxRetries = 3)
            }
        }
        val validResponse = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/plain\r\n")
            append("Transfer-Encoding: chunked\r\n\r\n")
            append("4\r\nTest\r\n0\r\n\r\n")
        }

        client.use {
            serverSocket.close()

            ServerSocket(serverSocket.localPort).use { server ->
                val thread = thread {
                    val prematureDisconnect = {
                        server.accept().use { socket ->
                            socket.getOutputStream().use { out ->
                                out.write(validResponse.toByteArray(), 0, validResponse.length - 10)
                            }
                        }
                    }
                    val respondCorrectly = {
                        server.accept().use { socket ->
                            socket.getOutputStream().use { out ->
                                out.write(validResponse.toByteArray())
                            }
                        }
                    }
                    runCatching(prematureDisconnect)
                    runCatching(prematureDisconnect)
                    runCatching(respondCorrectly)
                }

                val response = client.get("http://localhost:${serverSocket.localPort}/")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Test", response.bodyAsText())
                thread.join()
            }
        }
    }

    @Test
    fun testLateServerStart(): Unit = runBlocking {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withECDSA", keySizeInBits = 256)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }

        HttpClient(
            CIO.config {
                maxConnectionsCount = 3

                endpoint {
                    connectTimeout = SOCKET_CONNECT_TIMEOUT
                    connectAttempts = 1
                }

                https {
                    trustManager = trustManagerFactory.trustManagers
                        .first { it is X509TrustManager } as X509TrustManager
                }
            }
        ).use { client ->
            val serverPort = ServerSocket(0).use { it.localPort }
            val server = embeddedServer(
                Netty,
                serverConfig {
                    module {
                        routing {
                            get {
                                call.respondText("OK")
                            }
                        }
                    }
                }
            ) {
                sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                    port = serverPort
                    keyStorePath = keyStoreFile.absoluteFile
                }
            }

            try {
                client.get { url(scheme = "https", path = "/", port = serverPort) }.body<String>()
            } catch (_: ConnectException) {
            }

            try {
                server.start(wait = false)

                val message = client.get { url(scheme = "https", path = "/", port = serverPort) }.body<String>()
                assertEquals("OK", message)
            } finally {
                server.stop(0, 0, TimeUnit.MILLISECONDS)
            }
        }
    }

    companion object {
        // It is important to have it greater than 1100 ms
        // because Windows, in average, takes slightly more than a second to produce connection refused error
        // even over loopback.
        // So instead of a connection refused error it may produce a timeout exception
        private const val SOCKET_CONNECT_TIMEOUT: Long = 2000
    }
}
