/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

internal class ByteChannelReplay(private val origin: ByteReadChannel) {
    private val content: AtomicRef<CompletableDeferred<ByteArray>?> = atomic(null)

    @OptIn(DelicateCoroutinesApi::class)
    fun replay(): ByteReadChannel {
        if (origin.closedCause != null) {
            throw origin.closedCause!!
        }

        var deferred: CompletableDeferred<ByteArray>? = content.value
        if (deferred == null) {
            deferred = CompletableDeferred()
            if (!content.compareAndSet(null, deferred)) {
                deferred = content.value
            } else {
                return receiveBody(deferred)
            }
        }

        return GlobalScope.writer {
            val body = deferred!!.await()
            channel.writeFully(body)
        }.channel
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun receiveBody(
        result: CompletableDeferred<ByteArray>
    ): ByteReadChannel = GlobalScope.writer(Dispatchers.Unconfined) {
        val body = BytePacketBuilder()
        try {
            while (!origin.isClosedForRead) {
                if (origin.availableForRead == 0) origin.awaitContent()
                val packet = origin.readPacket(origin.availableForRead)

                body.writePacket(packet.copy())
                channel.writePacket(packet)
                channel.flush()
            }

            origin.closedCause?.let { throw it }
            result.complete(body.build().readBytes())
        } catch (cause: Throwable) {
            body.release()
            result.completeExceptionally(cause)
            throw cause
        }
    }.channel
}
