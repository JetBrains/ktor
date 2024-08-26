/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Creates a RAW web socket session from connection
 *
 * @param input is a [ByteReadChannel] of connection
 * @param output is a [ByteWriteChannel] of connection
 * @param maxFrameSize is an initial [maxFrameSize] value for [WebSocketSession]
 * @param masking is an initial [masking] value for [WebSocketSession]
 * @param coroutineContext is a [CoroutineContext] to execute reading/writing from/to connection
 */
@Suppress("FunctionName")
public actual fun RawWebSocket(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    maxFrameSize: Long,
    masking: Boolean,
    coroutineContext: CoroutineContext
): WebSocketSession = RawWebSocketCommon(input, output, maxFrameSize, masking, coroutineContext)
