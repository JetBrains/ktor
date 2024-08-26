// ktlint-disable filename
/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

internal class MemoryCache(
    val body: ByteReadChannel,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : DoubleReceiveCache {
    private var fullBody: ByteArray? = null
    private var cause: Throwable? = null

    @OptIn(DelicateCoroutinesApi::class)
    private val reader: ByteReadChannel = GlobalScope.writer(coroutineContext) {
        val buffer = ByteArrayPool.borrow()
        val packet = BytePacketBuilder()
        while (!body.isClosedForRead) {
            val size = body.readAvailable(buffer)
            if (size == -1) break
            packet.writeFully(buffer, 0, size)

            channel.writeFully(buffer, 0, size)
        }

        if (body.closedCause != null) {
            cause = body.closedCause
            channel.close(body.closedCause)
        }

        fullBody = packet.build().readByteArray()
    }.channel

    override fun read(): ByteReadChannel {
        val currentCause = cause
        if (currentCause != null) {
            return ByteChannel().apply { close(currentCause) }
        }

        return fullBody?.let { ByteReadChannel(it) } ?: reader
    }

    override fun dispose() {
    }
}
