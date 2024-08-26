/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.okhttp

import io.ktor.client.plugins.websocket.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import okhttp3.*
import okio.*
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.*

internal class OkHttpWebsocketSession(
    private val engine: OkHttpClient,
    private val webSocketFactory: WebSocket.Factory,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext
) : DefaultWebSocketSession, WebSocketListener() {
    // Deferred reference to "this", completed only after the object successfully constructed.
    private val self = CompletableDeferred<OkHttpWebsocketSession>()

    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()

    override var pingIntervalMillis: Long
        get() = engine.pingIntervalMillis.toLong()
        set(_) = throw WebSocketException(
            "OkHttp doesn't support dynamic ping interval. You could switch it in the engine configuration."
        )

    override var timeoutMillis: Long
        get() = engine.readTimeoutMillis.toLong()
        set(_) = throw WebSocketException("Websocket timeout should be configured in OkHttp engine.")

    override var masking: Boolean
        get() = true
        set(_) = throw WebSocketException("Masking switch is not supported in OkHttp engine.")

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) = throw WebSocketException("Max frame size switch is not supported in OkHttp engine.")

    private val _incoming = Channel<Frame>()
    private val _closeReason = CompletableDeferred<CloseReason?>()

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val closeReason: Deferred<CloseReason?>
        get() = _closeReason

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        require(negotiatedExtensions.isEmpty()) { "Extensions are not supported." }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override val outgoing: SendChannel<Frame> = actor {
        val websocket: WebSocket = webSocketFactory.newWebSocket(engineRequest, self.await())
        var closeReason = DEFAULT_CLOSE_REASON_ERROR

        try {
            for (frame in channel) {
                when (frame) {
                    is Frame.Binary -> websocket.send(frame.data.toByteString(0, frame.data.size))
                    is Frame.Text -> websocket.send(String(frame.data))
                    is Frame.Close -> {
                        val outgoingCloseReason = frame.readReason()!!
                        if (!outgoingCloseReason.isReserved()) {
                            closeReason = outgoingCloseReason
                        }
                        return@actor
                    }
                    else -> throw UnsupportedFrameTypeException(frame)
                }
            }
        } finally {
            try {
                websocket.close(closeReason.code.toInt(), closeReason.message)
            } catch (cause: Throwable) {
                websocket.cancel()
                throw cause
            }
        }
    }

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        originResponse.complete(response)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        _incoming.trySendBlocking(Frame.Binary(true, bytes.toByteArray()))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        _incoming.trySendBlocking(Frame.Text(true, text.toByteArray()))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)

        _closeReason.complete(CloseReason(code.toShort(), reason))
        _incoming.close()
        outgoing.close(
            CancellationException(
                "WebSocket session closed with code ${CloseReason.Codes.byCode(code.toShort())?.toString() ?: code}."
            )
        )
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)

        _closeReason.complete(CloseReason(code.toShort(), reason))
        try {
            outgoing.trySendBlocking(Frame.Close(CloseReason(code.toShort(), reason)))
        } catch (ignore: Throwable) {
        }
        _incoming.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)

        _closeReason.completeExceptionally(t)
        originResponse.completeExceptionally(t)
        _incoming.close(t)
        outgoing.close(t)
    }

    override suspend fun flush() {
    }

    /**
     * Creates a new web socket and starts the session.
     */
    fun start() {
        self.complete(this)
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        DeprecationLevel.ERROR
    )
    override fun terminate() {
        coroutineContext.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
public class UnsupportedFrameTypeException(
    private val frame: Frame
) : IllegalArgumentException("Unsupported frame type: $frame"), CopyableThrowable<UnsupportedFrameTypeException> {
    override fun createCopy(): UnsupportedFrameTypeException = UnsupportedFrameTypeException(frame).also {
        it.initCause(this)
    }
}

@OptIn(InternalAPI::class)
private fun CloseReason.isReserved() = CloseReason.Codes.byCode(code).let { recognized ->
    recognized == null || recognized == CloseReason.Codes.CLOSED_ABNORMALLY
}

private val DEFAULT_CLOSE_REASON_ERROR: CloseReason =
    CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Client failure")
