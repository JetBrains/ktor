/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.server.websocket

import io.ktor.websocket.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * WebSockets support plugin. It is required to be installed first before binding any websocket endpoints
 *
 * ```
 * install(WebSockets)
 *
 * install(Routing) {
 *     webSocket("/ws") {
 *          incoming.consumeForEach { ... }
 *     }
 * }
 * ```
 *
 * @param pingInterval duration between pings or `null` to disable pings.
 * @param timeout write/ping timeout after that a connection will be closed.
 * @param maxFrameSize maximum frame that could be received or sent.
 * @param masking whether masking need to be enabled (useful for security).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.websocket.WebSockets)
 */
public fun WebSockets(
    pingInterval: Duration?,
    timeout: Duration,
    maxFrameSize: Long,
    masking: Boolean,
): WebSockets = WebSockets(
    pingIntervalMillis = pingInterval?.inWholeMilliseconds ?: PINGER_DISABLED,
    timeoutMillis = timeout.inWholeMilliseconds,
    maxFrameSize = maxFrameSize,
    masking = masking,
)

public inline val WebSockets.pingInterval: Duration?
    get() = pingIntervalMillis.takeIf { it > PINGER_DISABLED }?.milliseconds

public inline val WebSockets.timeout: Duration
    get() = timeoutMillis.milliseconds

public inline var WebSockets.WebSocketOptions.pingPeriod: Duration?
    get() = pingPeriodMillis.takeIf { it > PINGER_DISABLED }?.milliseconds
    set(new) {
        pingPeriodMillis = new?.inWholeMilliseconds ?: PINGER_DISABLED
    }

public inline var WebSockets.WebSocketOptions.timeout: Duration
    get() = timeoutMillis.milliseconds
    set(new) {
        timeoutMillis = new.inWholeMilliseconds
    }
