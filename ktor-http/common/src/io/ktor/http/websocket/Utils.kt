/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.websocket

import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

private const val WEBSOCKET_SERVER_ACCEPT_TAIL = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

/**
 * Calculates `Sec-WebSocket-Accept` header value
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.websocket.websocketServerAccept)
 */
public fun websocketServerAccept(nonce: String): String =
    sha1("${nonce.trim()}$WEBSOCKET_SERVER_ACCEPT_TAIL".toByteArray(Charsets.ISO_8859_1)).encodeBase64()
