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
import kotlin.properties.*

/**
 * Creates a RAW web socket session from connection
 *
 * @param input is a [ByteReadChannel] of connection
 * @param output is a [ByteWriteChannel] of connection
 * @param maxFrameSize is an initial [maxFrameSize] value for [WebSocketSession]
 * @param masking is an initial [masking] value for [WebSocketSession]
 * @param coroutineContext is a [CoroutineContext] to execute reading/writing from/to connection
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.RawWebSocket)
 */
@Suppress("FunctionName")
public actual fun RawWebSocket(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    maxFrameSize: Long,
    masking: Boolean,
    coroutineContext: CoroutineContext
): WebSocketSession = RawWebSocketJvm(input, output, maxFrameSize, masking, coroutineContext)

internal class RawWebSocketJvm(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    masking: Boolean = false,
    coroutineContext: CoroutineContext,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : WebSocketSession {
    private val socketJob: CompletableJob = Job(coroutineContext[Job])
    private val filtered = Channel<Frame>(Channel.RENDEZVOUS)

    override val coroutineContext: CoroutineContext = coroutineContext + socketJob + CoroutineName("raw-ws")
    override val incoming: ReceiveChannel<Frame> get() = filtered
    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override var maxFrameSize: Long by Delegates.observable(maxFrameSize) { _, _, newValue ->
        reader.maxFrameSize = newValue
    }

    override var masking: Boolean by Delegates.observable(masking) { _, _, newValue ->
        writer.masking = newValue
    }

    internal val writer: WebSocketWriter = WebSocketWriter(output, this.coroutineContext, masking, pool)
    internal val reader: WebSocketReader = WebSocketReader(input, this.coroutineContext, maxFrameSize, pool)

    init {
        launch {
            try {
                for (frame in reader.incoming) {
                    filtered.send(frame)
                }
            } catch (cause: FrameTooBigException) {
                outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.TOO_BIG, cause.message)))
                filtered.close(cause)
            } catch (cause: ProtocolViolationException) {
                outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, cause.message)))
                filtered.close(cause)
            } catch (cause: CancellationException) {
                reader.incoming.cancel(cause)
            } catch (cause: Throwable) {
                filtered.close(cause)
            } finally {
                filtered.close()
            }
        }

        socketJob.complete()
    }

    override suspend fun flush(): Unit = writer.flush()

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        outgoing.close()
        socketJob.complete()
    }
}
