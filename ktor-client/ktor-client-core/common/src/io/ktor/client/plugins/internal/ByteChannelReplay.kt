/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.*

internal class ByteChannelReplay(private val origin: ByteReadChannel) {
    private val content: AtomicRef<CopyFromSourceTask?> = atomic(null)

    @OptIn(DelicateCoroutinesApi::class)
    fun replay(): ByteReadChannel {
        if (origin.closedCause != null) {
            throw origin.closedCause!!
        }

        var copyTask: CopyFromSourceTask? = content.value
        if (copyTask == null) {
            copyTask = CopyFromSourceTask()
            if (!content.compareAndSet(null, copyTask)) {
                copyTask = content.value
            } else {
                return copyTask.start()
            }
        }

        return GlobalScope.writer {
            val body = copyTask!!.awaitImpatiently()
            channel.writeFully(body)
        }.channel
    }

    /**
     * The first caller to get the body will stream from the origin, while copying packets to the saved body.
     *
     * This can be interrupted by the next caller if the first is taking too long to read. This still waits for the
     * origin to be copied to memory.
     */
    private inner class CopyFromSourceTask(
        val savedResponse: CompletableDeferred<ByteArray> = CompletableDeferred()
    ) {
        lateinit var writerJob: WriterJob

        fun start(): ByteReadChannel {
            writerJob = receiveBody()
            return writerJob.channel
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun receiveBody(): WriterJob = GlobalScope.writer(Dispatchers.Unconfined) {
            val body = BytePacketBuilder()
            try {
                while (!origin.isClosedForRead) {
                    if (origin.availableForRead == 0) origin.awaitContent()
                    val packet = origin.readPacket(origin.availableForRead)

                    try {
                        if (!channel.isClosedForWrite) {
                            channel.writePacket(packet.peek())
                            channel.flush()
                        }
                    } catch (_: Exception) {
                        // the reader may have abandoned this channel
                        // but we still want to write to the saved response
                    }
                    body.writePacket(packet)
                }

                origin.closedCause?.let { throw it }
                savedResponse.complete(body.build().readByteArray())
            } catch (cause: Throwable) {
                body.close()
                savedResponse.completeExceptionally(cause)
                throw cause
            }
        }

        suspend fun awaitImpatiently(): ByteArray {
            if (!writerJob.isCompleted) {
                writerJob.channel.cancel(SaveBodyAbandonedReadException())
            }
            return savedResponse.await()
        }
    }
}

/**
 * Thrown when a second attempt to read the body is made while the first call is blocked.
 */
public class SaveBodyAbandonedReadException : RuntimeException("Save body abandoned")
