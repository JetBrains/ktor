/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Class that processes written [outgoing] Websocket [Frame],
 * serializes them and writes the bits into the [writeChannel].
 * @property masking: whether it will mask serialized frames.
 * @property pool: [ByteBuffer] pool to be used by this writer
 */
public class WebSocketWriter(
    private val writeChannel: ByteWriteChannel,
    override val coroutineContext: CoroutineContext,
    public var masking: Boolean = false,
    public val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : CoroutineScope {

    private val queue = Channel<Any>(capacity = 8)

    private val serializer = Serializer()

    /**
     * Channel for sending Websocket's [Frame] that will be serialized and written to [writeChannel].
     */
    public val outgoing: SendChannel<Frame> get() = queue

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeLoopJob = launch(context = CoroutineName("ws-writer"), start = CoroutineStart.ATOMIC) {
        pool.useInstance { writeLoop(it) }
    }

    private suspend fun writeLoop(buffer: ByteBuffer) {
        buffer.clear()
        try {
            loop@ for (message in queue) {
                when (message) {
                    is Frame -> if (drainQueueAndSerialize(message, buffer)) break@loop
                    is FlushRequest -> {
                        // we don't need writeChannel.flush() here as
                        // we do flush at end of every drainQueueAndSerialize
                        message.complete()
                    }
                    else -> throw IllegalArgumentException("unknown message $message")
                }
            }
        } catch (cause: ChannelWriteException) {
            queue.close(CancellationException("Failed to write to WebSocket.", cause))
        } catch (t: Throwable) {
            queue.close(t)
        } finally {
            queue.close(CancellationException("WebSocket closed.", null))
            writeChannel.close()
        }

        drainQueueAndDiscard()
    }

    private fun drainQueueAndDiscard() {
        queue.close()

        try {
            do {
                val message = queue.tryReceive().getOrNull() ?: break
                when (message) {
                    is Frame.Close -> {
                    } // ignore
                    is Frame.Ping, is Frame.Pong -> {
                    } // ignore
                    is FlushRequest -> message.complete()
                    is Frame.Text, is Frame.Binary -> {
                    } // discard
                    else -> throw IllegalArgumentException("unknown message $message")
                }
            } while (true)
        } catch (_: CancellationException) {
        }
    }

    private suspend fun drainQueueAndSerialize(firstMsg: Frame, buffer: ByteBuffer): Boolean {
        var flush: FlushRequest? = null
        serializer.enqueue(firstMsg)
        var closeSent = firstMsg is Frame.Close

        // initially serializer has at least one message queued
        while (true) {
            while (flush == null && !closeSent && serializer.remainingCapacity > 0) {
                val message = queue.tryReceive().getOrNull() ?: break
                when (message) {
                    is FlushRequest -> flush = message
                    is Frame.Close -> {
                        serializer.enqueue(message)
                        closeSent = true
                    }
                    is Frame -> serializer.enqueue(message)
                    else -> throw IllegalArgumentException("unknown message $message")
                }
            }

            if (closeSent) {
                queue.close()
            }

            if (!serializer.hasOutstandingBytes && buffer.position() == 0) break

            serializer.masking = masking
            serializer.serialize(buffer)
            buffer.flip()

            do {
                writeChannel.writeFully(buffer)

                if (!serializer.hasOutstandingBytes && !buffer.hasRemaining()) {
                    flush?.let {
                        writeChannel.flush()
                        it.complete()
                        flush = null
                    }
                }
            } while ((flush != null || closeSent) && buffer.hasRemaining())
            // it is important here to not poll for more frames if we have flush request
            // otherwise flush completion could be delayed for too long while actually could be done

            buffer.compact()
        }

        // it is important here to flush the channel as some engines could delay actual bytes transferring
        // as we reached here then we don't have any outstanding messages so we can flush at idle
        writeChannel.flush()
        flush?.complete()

        return closeSent
    }

    /**
     * Send a frame and write it and all outstanding frames in the queue
     */
    public suspend fun send(frame: Frame): Unit = queue.send(frame)

    /**
     * Ensures all enqueued messages has been written
     */
    public suspend fun flush(): Unit = FlushRequest(coroutineContext[Job]).also {
        try {
            queue.send(it)
        } catch (closed: ClosedSendChannelException) {
            it.complete()
            writeLoopJob.join()
        } catch (sendFailure: Throwable) {
            it.complete()
            throw sendFailure
        }
    }.await()

    private class FlushRequest(parent: Job?) {
        private val done: CompletableJob = Job(parent)
        public fun complete(): Boolean = done.complete()
        suspend fun await(): Unit = done.join()
    }
}
