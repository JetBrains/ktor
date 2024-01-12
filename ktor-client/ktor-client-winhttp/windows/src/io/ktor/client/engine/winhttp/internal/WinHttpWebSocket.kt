/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.windows.*
import platform.winhttp.*
import kotlin.coroutines.*

private object WinHttpWebSocketBuffer {
    val BinaryMessage = WINHTTP_WEB_SOCKET_BINARY_MESSAGE_BUFFER_TYPE
    val BinaryFragment = WINHTTP_WEB_SOCKET_BINARY_FRAGMENT_BUFFER_TYPE
    val TextMessage = WINHTTP_WEB_SOCKET_UTF8_MESSAGE_BUFFER_TYPE
    val TextFragment = WINHTTP_WEB_SOCKET_UTF8_FRAGMENT_BUFFER_TYPE
    val Close = WINHTTP_WEB_SOCKET_CLOSE_BUFFER_TYPE
}

@OptIn(ExperimentalForeignApi::class)
internal class WinHttpWebSocket @OptIn(ExperimentalForeignApi::class) constructor(
    private val hWebSocket: COpaquePointer,
    private val connect: WinHttpConnect,
    callContext: CoroutineContext
) : WebSocketSession, Closeable {

    private val closed = atomic(false)
    private val socketJob = Job(callContext[Job])

    private val _incoming = Channel<Frame>(Channel.UNLIMITED)
    private val _outgoing = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = callContext + socketJob
    override var masking: Boolean
        get() = true
        set(_) {}

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) {}

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val outgoing: SendChannel<Frame>
        get() = _outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    init {
        socketJob.invokeOnCompletion {
            close(it)
        }

        connect.on(WinHttpCallbackStatus.CloseComplete) { _, _ ->
            onDisconnect()
        }

        // Start receiving frames
        launch {
            ByteArrayPool.useInstance { readBuffer ->
                while (!closed.value) {
                    val frame = readBuffer.usePinned { dst ->
                        receiveNextFrame(dst)
                    }
                    if (frame == null) {
                        socketJob.complete()
                        break
                    }
                    onFrame(frame, readBuffer)
                }
            }
        }

        // Start sending frames
        launch {
            while (!closed.value) {
                sendNextFrame()
            }
        }
    }

    private suspend fun receiveNextFrame(buffer: Pinned<ByteArray>): WinHttpWebSocketStatus? {
        return closeableCoroutine(connect, ERROR_FAILED_TO_RECEIVE_FRAME) { continuation ->
            connect.on(WinHttpCallbackStatus.ReadComplete) { statusInfo, _ ->
                if (statusInfo == null) {
                    val exception = IllegalStateException(ERROR_FAILED_TO_RECEIVE_FRAME)
                    continuation.resumeWithException(exception)
                } else {
                    val status = statusInfo.reinterpret<WINHTTP_WEB_SOCKET_STATUS>().pointed
                    continuation.resume(
                        WinHttpWebSocketStatus(
                            bufferType = status.eBufferType,
                            size = status.dwBytesTransferred.toInt()
                        )
                    )
                }
            }

            val data = if (buffer.get().isEmpty()) null else buffer.addressOf(0)

            if (WinHttpWebSocketReceive(
                    hWebSocket,
                    data,
                    buffer.get().size.convert(),
                    null,
                    null
                ) != 0u &&
                continuation.isActive
            ) {
                continuation.resume(null)
                return@closeableCoroutine
            }
        }
    }

    private fun onFrame(status: WinHttpWebSocketStatus, readBuffer: ByteArray) {
        when (status.bufferType) {
            WinHttpWebSocketBuffer.BinaryMessage -> {
                val data = readBuffer.copyOf(status.size)
                _incoming.trySend(Frame.Binary(fin = true, data = data))
            }

            WinHttpWebSocketBuffer.BinaryFragment -> {
                val data = readBuffer.copyOf(status.size)
                _incoming.trySend(Frame.Binary(fin = false, data = data))
            }

            WinHttpWebSocketBuffer.TextMessage -> {
                val data = readBuffer.copyOf(status.size)
                _incoming.trySend(Frame.Text(fin = true, data = data))
            }

            WinHttpWebSocketBuffer.TextFragment -> {
                val data = readBuffer.copyOf(status.size)
                _incoming.trySend(Frame.Text(fin = false, data = data))
            }

            WinHttpWebSocketBuffer.Close -> {
                val data = readBuffer.copyOf(status.size)
                _incoming.trySend(Frame.Close(data))
            }
        }
    }

    private suspend fun sendNextFrame() {
        val frame = _outgoing.receive()

        when (frame.frameType) {
            FrameType.TEXT -> {
                val type = if (frame.fin) {
                    WINHTTP_WEB_SOCKET_UTF8_MESSAGE_BUFFER_TYPE
                } else {
                    WINHTTP_WEB_SOCKET_UTF8_FRAGMENT_BUFFER_TYPE
                }
                frame.data.usePinned { src ->
                    sendFrame(type, src)
                }
            }

            FrameType.BINARY,
            FrameType.PING,
            FrameType.PONG -> {
                val type = if (frame.fin) {
                    WINHTTP_WEB_SOCKET_BINARY_MESSAGE_BUFFER_TYPE
                } else {
                    WINHTTP_WEB_SOCKET_BINARY_FRAGMENT_BUFFER_TYPE
                }
                frame.data.usePinned { src ->
                    sendFrame(type, src)
                }
            }

            FrameType.CLOSE -> {
                val data = buildPacket { writeFully(frame.data) }
                val code = data.readShort().toInt()
                val reason = data.readText()
                sendClose(code, reason)
                socketJob.complete()
            }
        }
    }

    private suspend fun sendFrame(
        type: WINHTTP_WEB_SOCKET_BUFFER_TYPE,
        buffer: Pinned<ByteArray>
    ) {
        return closeableCoroutine(connect, "") { continuation ->
            connect.on(WinHttpCallbackStatus.WriteComplete) { _, _ ->
                continuation.resume(Unit)
            }

            val data = if (buffer.get().isEmpty()) null else buffer.addressOf(0)

            if (WinHttpWebSocketSend(
                    hWebSocket,
                    type,
                    data,
                    buffer.get().size.convert()
                ) != 0u
            ) {
                throw getWinHttpException("Unable to send data to WebSocket")
            }
        }
    }

    private fun sendClose(code: Int, reason: String) {
        val reasonBytes = reason.ifEmpty { null }?.toByteArray()
        val buffer = reasonBytes?.pin()
        try {
            if (WinHttpWebSocketShutdown(
                    hWebSocket,
                    code.convert(),
                    buffer?.addressOf(0),
                    reasonBytes?.size?.convert() ?: 0u
                ) != 0u
            ) {
                throw getWinHttpException("Unable to close WebSocket")
            }
        } finally {
            buffer?.unpin()
        }
    }

    private fun onDisconnect() = memScoped {
        if (closed.value) return@memScoped

        val status = alloc<UShortVar>()
        val reason = allocArray<ShortVar>(123)
        val reasonLengthConsumed = alloc<UIntVar>()

        try {
            if (WinHttpWebSocketQueryCloseStatus(
                    hWebSocket,
                    status.ptr,
                    null,
                    0.convert(),
                    reasonLengthConsumed.ptr
                ) != 0u
            ) {
                return@memScoped
            }

            _incoming.trySend(
                Frame.Close(
                    CloseReason(
                        code = status.value.convert<Short>(),
                        message = reason.toKStringFromUtf16()
                    )
                )
            )
        } finally {
            socketJob.complete()
        }
    }

    override suspend fun flush() = Unit

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        socketJob.cancel()
    }

    override fun close() {
        socketJob.complete()
    }

    private fun close(@Suppress("UNUSED_PARAMETER") cause: Throwable? = null) {
        if (!closed.compareAndSet(expect = false, update = true)) return

        _incoming.close()
        _outgoing.cancel()

        WinHttpWebSocketClose(
            hWebSocket,
            WINHTTP_WEB_SOCKET_SUCCESS_CLOSE_STATUS.convert(),
            NULL,
            0.convert()
        )
        WinHttpCloseHandle(hWebSocket)
        connect.close()
    }

    companion object {
        private const val ERROR_FAILED_TO_RECEIVE_FRAME = "Failed to receive frame"
    }

    private class WinHttpWebSocketStatus(val bufferType: UInt, val size: Int)
}
