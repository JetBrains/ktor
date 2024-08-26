/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(DelicateCoroutinesApi::class, ExperimentalForeignApi::class)
internal fun WinHttpRequest.readBody(callContext: CoroutineContext): ByteReadChannel {
    return GlobalScope.writer(callContext) {
        val readBuffer = ByteArrayPool.borrow()
        try {
            while (callContext.isActive) {
                val availableBytes = queryDataAvailable()
                if (availableBytes <= 0) {
                    break
                }
                val bytesToRead = minOf(availableBytes, readBuffer.size)
                val readBytes = readBuffer.usePinned { dst ->
                    readData(dst, bytesToRead)
                }
                channel.writeFully(readBuffer, 0, readBytes)
            }
        } finally {
            ByteArrayPool.recycle(readBuffer)
        }
    }.channel
}
