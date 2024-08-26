/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.test.*
import kotlin.test.Test

abstract class ConnectionTestSuite(val engine: ApplicationEngineFactory<*, *>) {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testNetworkAddresses() = runBlocking {
        val server = embeddedServer(
            engine,
            applicationProperties {}
        ) {
            connector { port = 0 }
            connector { port = ServerSocket(0).use { it.localPort } }
        }

        GlobalScope.launch {
            server.start(true)
        }

        val addresses = withTimeout(15000) {
            server.engine.resolvedConnectors()
        }

        assertEquals(2, addresses.size)
        assertFalse(addresses.any { it.port == 0 })
        server.stop(50, 1000)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testServerReadyEvent() = runBlocking {
        val serverStarted = CompletableDeferred<Unit>()
        val serverPort = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
        val server = embeddedServer(
            engine,
            applicationProperties(applicationEnvironment()) {
                module {
                    routing {
                        get("/") {
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }
            }
        ) {
            connector { port = serverPort }
        }

        server.monitor.subscribe(ServerReady) {
            serverStarted.complete(Unit)
        }

        GlobalScope.launch {
            server.start(true)
        }

        withTimeout(5000) {
            serverStarted.join()
            val response = HttpClient(CIO).get("http://127.0.0.1:$serverPort/")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        server.stop(50, 100)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testIpv6ServerHostAndPort() = runBlocking {
        val serverStarted = CompletableDeferred<Unit>()
        val serverPort = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
        val server = embeddedServer(
            engine,
            applicationProperties(applicationEnvironment()) {
                module {
                    routing {
                        get("/") {
                            val local = call.request.local
                            call.respondText { "${local.serverHost}:${local.serverPort}" }
                        }
                    }
                }
            }
        ) {
            connector { port = serverPort }
        }

        server.monitor.subscribe(ServerReady) {
            serverStarted.complete(Unit)
        }

        GlobalScope.launch {
            server.start(true)
        }

        withTimeout(5000) {
            serverStarted.join()
            val client = HttpClient(CIO)
            client.get("http://[::1]:$serverPort/").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("[::1]:$serverPort", response.bodyAsText())
            }
            client.get("http://[0:0:0:0:0:0:0:1]:$serverPort/").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("[0:0:0:0:0:0:0:1]:$serverPort", response.bodyAsText())
            }
            client.close()
        }

        server.stop(50, 100)
    }
}
