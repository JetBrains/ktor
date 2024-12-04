/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.websocket

import io.ktor.websocket.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Client WebSocket plugin.
 *
 * @param pingInterval - interval between [FrameType.PING] messages.
 * @param maxFrameSize - max size of a single websocket frame.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.websocket.WebSockets)
 */
public fun WebSockets(
    pingInterval: Duration?,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
): WebSockets = WebSockets(
    pingIntervalMillis = pingInterval?.inWholeMilliseconds ?: PINGER_DISABLED,
    maxFrameSize = maxFrameSize,
    extensionsConfig = WebSocketExtensionsConfig(),
)

/** Interval between [FrameType.PING] messages. */
public inline val WebSockets.pingInterval: Duration?
    get() = pingIntervalMillis.takeIf { it > PINGER_DISABLED }?.milliseconds

/**
 * Sets interval of sending [FrameType.PING] messages.
 *
 * Use `null` to disable ping.
 */
public inline var WebSockets.Config.pingInterval: Duration?
    get() = pingIntervalMillis.takeIf { it > PINGER_DISABLED }?.milliseconds
    set(new) {
        pingIntervalMillis = new?.inWholeMilliseconds ?: PINGER_DISABLED
    }
