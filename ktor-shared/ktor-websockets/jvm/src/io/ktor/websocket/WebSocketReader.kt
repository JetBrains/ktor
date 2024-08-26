/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.*

/**
 * Class that continuously reads a [byteChannel] and
 * converts into Websocket [Frame] exposing them in [incoming].
 *
 * @param maxFrameSize maximum frame size that could be read
 */
public class WebSocketReader(
    private val byteChannel: ByteReadChannel,
    override val coroutineContext: CoroutineContext,
    public var maxFrameSize: Long,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : CoroutineScope {
    private var state = State.HEADER
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()

    private val queue = Channel<Frame>(8)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val readerJob = launch(CoroutineName("ws-reader"), start = CoroutineStart.ATOMIC) {
        val buffer = pool.borrow()
        try {
            readLoop(buffer)
        } catch (expected: ClosedChannelException) {
        } catch (expected: CancellationException) {
        } catch (io: IOException) {
            queue.cancel()
        } catch (cause: FrameTooBigException) {
            // Bypass exception via queue to prevent cancellation and handle it on the top level.
            queue.close(cause)
        } catch (cause: ProtocolViolationException) {
            // same as above
            queue.close(cause)
        } catch (cause: Throwable) {
            throw cause
        } finally {
            pool.recycle(buffer)
            queue.close()
        }
    }

    /**
     * Channel receiving Websocket's [Frame] objects read from [byteChannel].
     */
    public val incoming: ReceiveChannel<Frame> get() = queue

    private suspend fun readLoop(buffer: ByteBuffer) {
        buffer.clear()

        while (state != State.CLOSED) {
            if (byteChannel.readAvailable(buffer) == -1) {
                state = State.CLOSED
                break
            }

            buffer.flip()
            parseLoop(buffer)
            buffer.compact()
        }
    }

    private suspend fun parseLoop(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            when (state) {
                State.HEADER -> {
                    frameParser.frame(buffer)

                    if (frameParser.bodyReady) {
                        state = State.BODY
                        if (frameParser.length > Int.MAX_VALUE || frameParser.length > maxFrameSize) {
                            throw FrameTooBigException(frameParser.length)
                        }

                        collector.start(frameParser.length.toInt(), buffer)
                        handleFrameIfProduced()
                    } else {
                        return
                    }
                }
                State.BODY -> {
                    collector.handle(buffer)

                    handleFrameIfProduced()
                }
                State.CLOSED -> return
            }
        }
    }

    private suspend fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state = if (frameParser.frameType == FrameType.CLOSE) State.CLOSED else State.HEADER

            val frame = with(frameParser) {
                Frame.byType(fin, frameType, collector.take(maskKey).moveToByteArray(), rsv1, rsv2, rsv3)
            }

            queue.send(frame)
            frameParser.bodyComplete()
        }
    }

    private enum class State {
        HEADER,
        BODY,
        CLOSED
    }
}
