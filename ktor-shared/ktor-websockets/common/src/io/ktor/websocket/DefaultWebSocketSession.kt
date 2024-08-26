/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*

internal val LOGGER = KtorSimpleLogger("io.ktor.websocket.WebSocket")

/**
 * A default WebSocket session with ping-pong and timeout processing and built-in [closeReason] population.
 */
public interface DefaultWebSocketSession : WebSocketSession {

    /**
     * Specifies the ping interval or `-1L` to disable pinger. Note that pongs will be handled despite this setting.
     */
    public var pingIntervalMillis: Long

    /**
     * Specifies a timeout to wait for pong reply to ping; otherwise, the session will be terminated immediately.
     * It doesn't have any effect if [pingIntervalMillis] is `-1` (pinger is disabled).
     */
    public var timeoutMillis: Long

    /**
     * A close reason for this session. It could be `null` if a session is terminated with no close reason
     * (for example due to connection failure).
     */
    public val closeReason: Deferred<CloseReason?>

    /**
     * Starts a WebSocket conversation.
     *
     * @param negotiatedExtensions specify negotiated extensions list to use in current session.
     */
    @InternalAPI
    public fun start(negotiatedExtensions: List<WebSocketExtension<*>> = emptyList())
}

/**
 * Creates [DefaultWebSocketSession] from a session.
 */
public fun DefaultWebSocketSession(
    session: WebSocketSession,
    pingInterval: Long = -1L,
    timeoutMillis: Long = 15000L
): DefaultWebSocketSession {
    require(session !is DefaultWebSocketSession) { "Cannot wrap other DefaultWebSocketSession" }
    return DefaultWebSocketSessionImpl(session, pingInterval, timeoutMillis)
}

private val IncomingProcessorCoroutineName = CoroutineName("ws-incoming-processor")
private val OutgoingProcessorCoroutineName = CoroutineName("ws-outgoing-processor")

private val NORMAL_CLOSE = CloseReason(CloseReason.Codes.NORMAL, "OK")

/**
 * A default WebSocket session implementation that handles ping-pongs, close sequence and frame fragmentation.
 */

internal class DefaultWebSocketSessionImpl(
    private val raw: WebSocketSession,
    pingInterval: Long,
    timeoutMillis: Long
) : DefaultWebSocketSession, WebSocketSession {
    private val pinger = atomic<SendChannel<Frame.Pong>?>(null)
    private val closeReasonRef = CompletableDeferred<CloseReason>()
    private val filtered = Channel<Frame>(8)
    private val outgoingToBeProcessed = Channel<Frame>(OUTGOING_CHANNEL_CAPACITY)
    private val closed: AtomicBoolean = atomic(false)
    private val context = Job(raw.coroutineContext[Job])

    private val _extensions: MutableList<WebSocketExtension<*>> = mutableListOf()
    private val started = atomic(false)

    override val incoming: ReceiveChannel<Frame> get() = filtered

    override val outgoing: SendChannel<Frame> get() = outgoingToBeProcessed

    override val extensions: List<WebSocketExtension<*>>
        get() = _extensions

    override val coroutineContext: CoroutineContext = raw.coroutineContext + context + CoroutineName("ws-default")

    override var masking: Boolean
        get() = raw.masking
        set(value) {
            raw.masking = value
        }

    override var maxFrameSize: Long
        get() = raw.maxFrameSize
        set(value) {
            raw.maxFrameSize = value
        }

    override var pingIntervalMillis: Long = pingInterval
        set(newValue) {
            field = newValue
            runOrCancelPinger()
        }

    override var timeoutMillis: Long = timeoutMillis
        set(newValue) {
            field = newValue
            runOrCancelPinger()
        }

    override val closeReason: Deferred<CloseReason?> = closeReasonRef

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        if (!started.compareAndSet(false, true)) {
            error("WebSocket session $this is already started.")
        }

        LOGGER.trace(
            "Starting default WebSocketSession($this) " +
                "with negotiated extensions: ${negotiatedExtensions.joinToString()}"
        )

        _extensions.addAll(negotiatedExtensions)
        runOrCancelPinger()
        runIncomingProcessor(ponger(outgoing))
        runOutgoingProcessor()
    }

    /**
     * Close session with GOING_AWAY reason
     */
    suspend fun goingAway(message: String = "Server is going down") {
        sendCloseSequence(CloseReason(CloseReason.Codes.GOING_AWAY, message))
    }

    override suspend fun flush() {
        raw.flush()
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        context.cancel()
        raw.cancel()
    }

    @OptIn(InternalAPI::class)
    private fun runIncomingProcessor(ponger: SendChannel<Frame.Ping>): Job = launch(
        IncomingProcessorCoroutineName + Dispatchers.Unconfined
    ) {
        var firstFrame: Frame? = null
        var frameBody: Sink? = null
        var closeFramePresented = false
        try {
            @OptIn(DelicateCoroutinesApi::class)
            raw.incoming.consumeEach { frame ->
                LOGGER.trace("WebSocketSession($this) receiving frame $frame")
                when (frame) {
                    is Frame.Close -> {
                        if (!outgoing.isClosedForSend) {
                            outgoing.send(Frame.Close(frame.readReason() ?: NORMAL_CLOSE))
                        }
                        closeFramePresented = true
                        return@launch
                    }

                    is Frame.Pong -> pinger.value?.send(frame)
                    is Frame.Ping -> ponger.send(frame)
                    else -> {
                        checkMaxFrameSize(frameBody, frame)

                        if (!frame.fin) {
                            if (firstFrame == null) {
                                firstFrame = frame
                            }
                            if (frameBody == null) {
                                frameBody = BytePacketBuilder()
                            }

                            frameBody!!.writeFully(frame.data)
                            return@consumeEach
                        }

                        if (firstFrame == null) {
                            filtered.send(processIncomingExtensions(frame))
                            return@consumeEach
                        }

                        frameBody!!.writeFully(frame.data)
                        val defragmented = Frame.byType(
                            fin = true,
                            firstFrame!!.frameType,
                            frameBody!!.build().readByteArray(),
                            firstFrame!!.rsv1,
                            firstFrame!!.rsv2,
                            firstFrame!!.rsv3
                        )

                        firstFrame = null
                        filtered.send(processIncomingExtensions(defragmented))
                    }
                }
            }
        } catch (ignore: ClosedSendChannelException) {
        } catch (cause: Throwable) {
            ponger.close()
            filtered.close(cause)
        } finally {
            ponger.close()
            frameBody?.close()
            filtered.close()

            if (!closeFramePresented) {
                close(CloseReason(CloseReason.Codes.CLOSED_ABNORMALLY, "Connection was closed without close frame"))
            }
        }
    }

    private fun runOutgoingProcessor(): Job = launch(
        OutgoingProcessorCoroutineName + Dispatchers.Unconfined,
        start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            outgoingProcessorLoop()
        } catch (ignore: ClosedSendChannelException) {
        } catch (ignore: ClosedReceiveChannelException) {
        } catch (ignore: CancellationException) {
            sendCloseSequence(CloseReason(CloseReason.Codes.NORMAL, ""))
        } catch (ignore: ChannelIOException) {
        } catch (cause: Throwable) {
            outgoingToBeProcessed.cancel(CancellationException("Failed to send frame", cause))
            raw.closeExceptionally(cause)
        } finally {
            outgoingToBeProcessed.cancel()
            raw.close()
        }
    }

    private suspend fun outgoingProcessorLoop() {
        for (frame in outgoingToBeProcessed) {
            LOGGER.trace("Sending $frame from session $this")
            val processedFrame: Frame = when (frame) {
                is Frame.Close -> {
                    sendCloseSequence(frame.readReason())
                    break
                }

                is Frame.Text,
                is Frame.Binary -> processOutgoingExtensions(frame)

                else -> frame
            }

            raw.outgoing.send(processedFrame)
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun sendCloseSequence(reason: CloseReason?, exception: Throwable? = null) {
        if (!tryClose()) return
        LOGGER.trace("Sending Close Sequence for session $this with reason $reason and exception $exception")
        context.complete()

        val reasonToSend = reason ?: CloseReason(CloseReason.Codes.NORMAL, "")
        try {
            runOrCancelPinger()
            if (reasonToSend.code != CloseReason.Codes.CLOSED_ABNORMALLY.code) {
                raw.outgoing.send(Frame.Close(reasonToSend))
            }
        } finally {
            closeReasonRef.complete(reasonToSend)

            if (exception != null) {
                outgoingToBeProcessed.close(exception)
                filtered.close(exception)
            }
        }
    }

    private fun tryClose(): Boolean = closed.compareAndSet(false, true)

    private fun runOrCancelPinger() {
        val interval = pingIntervalMillis

        val newPinger: SendChannel<Frame.Pong>? = when {
            closed.value -> null
            interval > 0L -> pinger(raw.outgoing, interval, timeoutMillis) {
                sendCloseSequence(it, IOException("Ping timeout"))
            }

            else -> null
        }

        // pinger is always lazy so we publish it first and then start it by sending EmptyPong
        // otherwise it may send ping before it get published so corresponding pong will not be dispatched to pinger
        // that will cause it to terminate connection on timeout
        pinger.getAndSet(newPinger)?.close()

        // it is safe here to send dummy pong because pinger will ignore it
        newPinger?.trySend(EmptyPong)?.isSuccess

        if (closed.value && newPinger != null) {
            runOrCancelPinger()
        }
    }

    private suspend fun checkMaxFrameSize(
        packet: Sink?,
        frame: Frame
    ) {
        val size = frame.data.size + (packet?.size ?: 0)
        if (size > maxFrameSize) {
            packet?.close()
            close(CloseReason(CloseReason.Codes.TOO_BIG, "Frame is too big: $size. Max size is $maxFrameSize"))
            throw FrameTooBigException(size.toLong())
        }
    }

    private fun processIncomingExtensions(frame: Frame): Frame =
        extensions.fold(frame) { current, extension -> extension.processIncomingFrame(current) }

    private fun processOutgoingExtensions(frame: Frame): Frame =
        extensions.fold(frame) { current, extension -> extension.processOutgoingFrame(current) }

    companion object {
        private val EmptyPong = Frame.Pong(ByteArray(0), NonDisposableHandle)
    }
}

internal expect val OUTGOING_CHANNEL_CAPACITY: Int
