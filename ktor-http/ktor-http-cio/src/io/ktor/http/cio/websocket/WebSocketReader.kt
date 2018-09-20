package io.ktor.http.cio.websocket

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.pool.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

/**
 * Class that continuously reads a [byteChannel] and
 * converts into Websocket [Frame] exposing them in [incoming].
 *
 * @param maxFrameSize maximum frame size that could be read
 */
@WebSocketInternalAPI
class WebSocketReader(
        private val byteChannel: ByteReadChannel,
        override val coroutineContext: CoroutineContext,
        var maxFrameSize: Long,
        pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : CoroutineScope {

    @Deprecated("Pass parent through the coroutine context",
            replaceWith = ReplaceWith("WebSocketReader(byteChannel, coroutineContext, maxFrameSize, pool)"))
    constructor(byteChannel: ByteReadChannel, maxFrameSize: Long,
                parent: Job?, coroutineContext: CoroutineContext, pool: ObjectPool<ByteBuffer> = KtorDefaultPool)
            : this(byteChannel, coroutineContext, maxFrameSize, pool)

    private var state = State.HEADER
    @Suppress("DEPRECATION")
    private val frameParser = FrameParser()
    @Suppress("DEPRECATION")
    private val collector = SimpleFrameCollector()

    private val queue = Channel<Frame>(8)

    private val readerJob = launch(start = CoroutineStart.LAZY) {
        val buffer = pool.borrow()
        try {
            readLoop(buffer)
        } catch (expected: ClosedChannelException) {
        } catch (expected: CancellationException) {
            queue.cancel()
        } catch (cause: Throwable) {
            queue.close(cause)
            throw cause
        } finally {
            pool.recycle(buffer)
            queue.close()
        }
    }

    /**
     * Channel receiving Websocket's [Frame] objects read from [byteChannel].
     */
    val incoming: ReceiveChannel<Frame> get() = queue.also { readerJob.start() }

    private suspend fun readLoop(buffer: ByteBuffer) {
        buffer.clear()

        while (true) {
            if (byteChannel.readAvailable(buffer) == -1) {
                state = State.END
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
                State.END -> return
            }
        }
    }

    private suspend fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state = State.HEADER
            queue.send(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    /**
     * Raised when the frame is bigger than allowed in a current websocket session
     * @param frameSize size of received or posted frame that is too big
     */
    class FrameTooBigException(val frameSize: Long) : Exception() {
        override val message: String
            get() = "Frame is too big: $frameSize"
    }

    private enum class State {
        HEADER,
        BODY,
        END
    }
}