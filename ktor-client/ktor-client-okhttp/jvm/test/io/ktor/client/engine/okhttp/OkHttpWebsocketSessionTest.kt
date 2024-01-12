/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import okhttp3.*
import kotlin.test.*
import kotlin.test.assertFailsWith

class OkHttpWebsocketSessionTest {

    @Test
    fun testOnFailure() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com").build()
        val coroutineContext = Job()
        val session = OkHttpWebsocketSession(client, client, request, coroutineContext)
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val exception = RuntimeException()
        session.onFailure(webSocket, exception, null)

        val job = Job()
        session.closeReason.invokeOnCompletion { cause ->
            assertEquals(exception, cause)
            job.complete()
        }
        runBlocking { job.join() }
    }

    @Test
    fun testWebSocketFactory() {
        val client = HttpClient(OkHttp) {
            install(WebSockets)

            engine {
                webSocketFactory = object : WebSocket.Factory {
                    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                        throw FactoryUsedException()
                    }
                }
            }
        }

        runBlocking {
            assertFailsWith<FactoryUsedException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                }
            }
        }
    }
}

private class FactoryUsedException : Exception()
