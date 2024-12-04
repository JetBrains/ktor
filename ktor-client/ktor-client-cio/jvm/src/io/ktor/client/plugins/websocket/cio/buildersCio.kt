/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.websocket.cio

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

/**
 * Creates a raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.websocket.cio.webSocketRawSession)
 */
public suspend fun HttpClient.webSocketRawSession(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientWebSocketSession {
    val request = prepareRequest {
        this.method = method
        url("ws", host, port, path)
        block()
    }

    val result = CompletableDeferred<ClientWebSocketSession>()

    launch {
        try {
            request.body<ClientWebSocketSession, Unit> { session ->
                val sessionCompleted = CompletableDeferred<Unit>()
                result.complete(session)
                session.outgoing.invokeOnClose {
                    if (it != null) {
                        sessionCompleted.completeExceptionally(it)
                    } else sessionCompleted.complete(Unit)
                }
                sessionCompleted.await()
            }
        } catch (cause: Throwable) {
            result.completeExceptionally(cause)
        }
    }

    return result.await()
}

/**
 * Creates a raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
public suspend fun HttpClient.webSocketRaw(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientWebSocketSession.() -> Unit
) {
    val session = webSocketRawSession(method, host, port, path) {
        url.protocol = URLProtocol.WS
        if (port != null) url.port = port

        request()
    }

    try {
        session.block()
    } catch (cause: Throwable) {
        session.closeExceptionally(cause)
    } finally {
        session.close()
    }
}

/**
 * Creates a raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
public suspend fun HttpClient.wsRaw(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientWebSocketSession.() -> Unit
) {
    webSocketRaw(method, host, port, path, request, block)
}

/**
 * Create secure raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
public suspend fun HttpClient.wssRaw(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientWebSocketSession.() -> Unit
) {
    webSocketRaw(
        method,
        host,
        port,
        path,
        request = {
            url.protocol = URLProtocol.WSS
            if (port != null) url.port = port

            request()
        },
        block = block
    )
}
