/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.random.*

/**
 * Creates a RAW web socket session from connection.
 *
 * @param input is a [ByteReadChannel] of connection
 * @param output is a [ByteWriteChannel] of connection
 * @param maxFrameSize is an initial [maxFrameSize] value for [WebSocketSession]
 * @param masking is an initial [masking] value for [WebSocketSession]
 * @param coroutineContext is a [CoroutineContext] to execute reading/writing from/to connection
 */
@Suppress("FunctionName")
public expect fun RawWebSocket(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    masking: Boolean = false,
    coroutineContext: CoroutineContext
): WebSocketSession

@OptIn(ExperimentalCoroutinesApi::class, InternalAPI::class)
internal class RawWebSocketCommon(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    override var maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    override var masking: Boolean = false,
    coroutineContext: CoroutineContext
) : WebSocketSession {
    private val socketJob: CompletableJob = Job(coroutineContext[Job])

    private val _incoming = Channel<Frame>(capacity = 8)
    private val _outgoing = Channel<Any>(capacity = 8)

    private var lastOpcode = 0

    override val coroutineContext: CoroutineContext = coroutineContext + socketJob + CoroutineName("raw-ws")
    override val incoming: ReceiveChannel<Frame> get() = _incoming
    override val outgoing: SendChannel<Frame> get() = _outgoing
    override val extensions: List<WebSocketExtension<*>> get() = emptyList()

    private val writerJob = launch(context = CoroutineName("ws-writer"), start = CoroutineStart.ATOMIC) {
        try {
            mainLoop@ while (true) when (val message = _outgoing.receive()) {
                is Frame -> {
                    output.writeFrame(message, masking)
                    output.flush()
                    if (message is Frame.Close) break@mainLoop
                }
                is FlushRequest -> {
                    message.complete()
                }
                else -> throw IllegalArgumentException("unknown message $message")
            }
            _outgoing.close()
        } catch (cause: ChannelWriteException) {
            _outgoing.close(CancellationException("Failed to write to WebSocket.", cause))
        } catch (t: Throwable) {
            _outgoing.close(t)
        } finally {
            _outgoing.close(CancellationException("WebSocket closed.", null))
            output.close()
        }

        while (true) when (val message = _outgoing.tryReceive().getOrNull() ?: break) {
            is FlushRequest -> message.complete()
            else -> {}
        }
    }

    private val readerJob = launch(CoroutineName("ws-reader"), start = CoroutineStart.ATOMIC) {
        try {
            while (true) {
                val frame = input.readFrame(maxFrameSize, lastOpcode)
                if (!frame.frameType.controlFrame) {
                    lastOpcode = if (frame.fin) 0 else frame.frameType.opcode
                }
                _incoming.send(frame)
            }
        } catch (cause: FrameTooBigException) {
            outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.TOO_BIG, cause.message)))
            _incoming.close(cause)
        } catch (cause: ProtocolViolationException) {
            // same as above
            outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, cause.message)))
            _incoming.close(cause)
        } catch (cause: CancellationException) {
            _incoming.cancel(cause)
        } catch (eof: EOFException) {
            // no more bytes is possible to read
        } catch (eof: ClosedReceiveChannelException) {
            // no more bytes is possible to read
        } catch (io: ChannelIOException) {
            _incoming.cancel()
        } catch (cause: Throwable) {
            _incoming.close(cause)
            throw cause
        } finally {
            _incoming.close()
        }
    }

    init {
        socketJob.complete()
    }

    override suspend fun flush(): Unit = FlushRequest(coroutineContext[Job]).also {
        try {
            _outgoing.send(it)
        } catch (closed: ClosedSendChannelException) {
            it.complete()
            writerJob.join()
        } catch (sendFailure: Throwable) {
            it.complete()
            throw sendFailure
        }
    }.await()

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        outgoing.close()
        socketJob.complete()
    }

    private class FlushRequest(parent: Job?) {
        private val done: CompletableJob = Job(parent)
        fun complete(): Boolean = done.complete()
        suspend fun await(): Unit = done.join()
    }
}

@Suppress("DEPRECATION")
private fun ByteReadPacket.mask(maskKey: Int): ByteReadPacket = withMemory(4) { maskMemory ->
    maskMemory.storeIntAt(0, maskKey)
    buildPacket {
        repeat(remaining.toInt()) { i ->
            writeByte((readByte().toInt() xor (maskMemory[i % 4].toInt())).toByte())
        }
    }
}

/**
 * Serializes WebSocket [Frame] and writes the bits into the [ByteWriteChannel].
 * If [masking] is true, then data will be masked with random mask
 */
@InternalAPI // used in tests
public suspend fun ByteWriteChannel.writeFrame(frame: Frame, masking: Boolean) {
    val length = frame.data.size

    val flagsAndOpcode = frame.fin.flagAt(7) or
        frame.rsv1.flagAt(6) or
        frame.rsv2.flagAt(5) or
        frame.rsv3.flagAt(4) or
        frame.frameType.opcode

    writeByte(flagsAndOpcode.toByte())

    val formattedLength = when {
        length < 126 -> length
        length <= 0xffff -> 126
        else -> 127
    }

    val maskAndLength = masking.flagAt(7) or formattedLength

    writeByte(maskAndLength.toByte())

    when (formattedLength) {
        126 -> writeShort(length.toShort())
        127 -> writeLong(length.toLong())
    }

    val data = ByteReadPacket(frame.data)

    val maskedData = when (masking) {
        true -> {
            val maskKey = Random.nextInt()
            writeInt(maskKey)
            data.mask(maskKey)
        }
        false -> data
    }
    writePacket(maskedData)
}

/**
 * Reads bits from [ByteReadChannel] and converts into a WebSocket [Frame].
 *
 * @param maxFrameSize maximum frame size that could be read
 * @param lastOpcode last read opcode
 */
@InternalAPI // used in tests
public suspend fun ByteReadChannel.readFrame(maxFrameSize: Long, lastOpcode: Int): Frame {
    val flagsAndOpcode = readByte().toInt()
    val maskAndLength = readByte().toInt()

    val rawOpcode = flagsAndOpcode and 0x0f
    if (rawOpcode == 0 && lastOpcode == 0) {
        throw ProtocolViolationException("Can't continue finished frames")
    }
    val opcode = if (rawOpcode == 0) lastOpcode else rawOpcode
    val frameType = FrameType[opcode] ?: throw IllegalStateException("Unsupported opcode: $opcode")
    if (rawOpcode != 0 && lastOpcode != 0 && !frameType.controlFrame) {
        // trying to intermix data frames
        throw ProtocolViolationException("Can't start new data frame before finishing previous one")
    }

    val fin = flagsAndOpcode and 0x80 != 0
    if (frameType.controlFrame && !fin) {
        throw ProtocolViolationException("control frames can't be fragmented")
    }

    val length = when (val length = maskAndLength and 0x7f) {
        126 -> readShort().toLong() and 0xffff
        127 -> readLong()
        else -> length.toLong()
    }
    if (frameType.controlFrame && length > 125) {
        throw ProtocolViolationException("control frames can't be larger than 125 bytes")
    }

    val maskKey = when (maskAndLength and 0x80 != 0) {
        true -> readInt()
        false -> -1
    }

    if (length > Int.MAX_VALUE || length > maxFrameSize) {
        throw FrameTooBigException(length)
    }

    val data = readPacket(length.toInt())
    val maskedData = when (maskKey) {
        -1 -> data
        else -> data.mask(maskKey)
    }

    return Frame.byType(
        fin = fin,
        frameType = frameType,
        data = maskedData.readBytes(),
        rsv1 = flagsAndOpcode and 0x40 != 0,
        rsv2 = flagsAndOpcode and 0x20 != 0,
        rsv3 = flagsAndOpcode and 0x10 != 0
    )
}
