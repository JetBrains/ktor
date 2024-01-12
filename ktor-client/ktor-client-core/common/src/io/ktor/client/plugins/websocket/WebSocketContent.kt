/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.*

private const val WEBSOCKET_VERSION = "13"
private const val NONCE_SIZE = 16

@OptIn(InternalAPI::class)
internal class WebSocketContent : ClientUpgradeContent() {
    private val nonce: String = buildString {
        val nonce = generateNonce(NONCE_SIZE)
        append(nonce.encodeBase64())
    }

    override val headers: Headers = HeadersBuilder().apply {
        append(HttpHeaders.Upgrade, "websocket")
        append(HttpHeaders.Connection, "Upgrade")

        append(HttpHeaders.SecWebSocketKey, nonce)
        append(HttpHeaders.SecWebSocketVersion, WEBSOCKET_VERSION)
    }.build()

    override fun verify(headers: Headers) {
        val serverAccept = headers[HttpHeaders.SecWebSocketAccept]
            ?: error("Server should specify header ${HttpHeaders.SecWebSocketAccept}")

        val expectedAccept = websocketServerAccept(nonce)
        check(expectedAccept == serverAccept) {
            "Failed to verify server accept header. Expected: $expectedAccept, received: $serverAccept"
        }
    }

    override fun toString(): String = "WebSocketContent"
}
