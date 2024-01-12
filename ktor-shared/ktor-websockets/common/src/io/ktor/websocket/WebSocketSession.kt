/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * A WebSocket session between two peers.
 *
 * - [Server WebSockets](https://ktor.io/docs/websocket.html)
 * - [Client WebSockets](https://ktor.io/docs/websocket-client.html)
 *
 */
public interface WebSocketSession : CoroutineScope {
    /**
     * Enables or disables masking output messages by a random XOR mask.
     * Note that changing this flag on the fly could be applied to the messages already sent (enqueued earlier)
     * as the sending pipeline works asynchronously.
     */
    public var masking: Boolean

    /**
     * Specifies the frame size limit. A connection will be closed if violated.
     */
    public var maxFrameSize: Long

    /**
     * An incoming frames channel.
     * Note that if you use `webSocket` to handle a WebSockets session,
     * the incoming channel doesn't contain control frames such as the ping/pong or close frames.
     * If you need control over control frames, use the `webSocketRaw` function.
     */
    public val incoming: ReceiveChannel<Frame>

    /**
     * An outgoing frames channel. It could have limited capacity so sending too many frames may lead to suspension at
     * corresponding send invocations. It also may suspend if a peer doesn't read frames for some reason.
     */
    public val outgoing: SendChannel<Frame>

    /**
     * Negotiated WebSocket extensions.
     */
    public val extensions: List<WebSocketExtension<*>>

    /**
     * Enqueue a frame, may suspend if an outgoing queue is full. May throw an exception if the
     * outgoing channel is already closed, so it is impossible to transfer any message.
     * Frames that were sent after close frame could be silently ignored.
     * Note that a close frame could be sent automatically in reply to a peer's close frame unless it is
     * raw WebSocket session.
     */
    public suspend fun send(frame: Frame) {
        outgoing.send(frame)
    }

    /**
     * Flushes all outstanding messages and suspends until all earlier sent messages will be written.
     * Could be called at any time even after close. May return immediately if the connection is already terminated.
     * However, it may also fail with an exception (or cancellation) at any point due to a session failure.
     * Note that [flush] doesn't guarantee that frames were actually delivered.
     */
    public suspend fun flush()

    /**
     * Initiates a connection termination immediately. Termination may complete asynchronously.
     */
    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    public fun terminate()
}

/**
 * Finds the extensions using [WebSocketExtensionFactory].
 *
 * @return extension instance.
 * @throws [IllegalStateException] if the extension is not found.
 */
public fun <T : WebSocketExtension<*>> WebSocketSession.extension(extension: WebSocketExtensionFactory<*, T>): T =
    extensionOrNull(extension) ?: error("Extension $extension not found.")

/**
 * Searches the extensions using [WebSocketExtensionFactory].
 *
 * @return extension instance or `null` if the extension is not installed.
 */
@Suppress("UNCHECKED_CAST")
public fun <T : WebSocketExtension<*>> WebSocketSession.extensionOrNull(
    extension: WebSocketExtensionFactory<*, T>
): T? = extensions.firstOrNull { it.factory.key === extension.key } as? T?

/**
 * Enqueues a text frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
public suspend fun WebSocketSession.send(content: String): Unit = send(Frame.Text(content))

/**
 * Enqueues a final binary frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throws an exception if the channel is already closed.
 */
public suspend fun WebSocketSession.send(content: ByteArray): Unit = send(Frame.Binary(true, content))

/**
 * Sends a close frame with the specified [reason]. May suspend if the outgoing channel is full.
 * The specified [reason] could be ignored if there was already
 * close frame sent (for example in reply to a peer close frame). It also may do nothing when a session or an outgoing
 * channel is already closed due to any reason.
 */
public suspend fun WebSocketSession.close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
    try {
        send(Frame.Close(reason))
        flush()
    } catch (_: Throwable) {
    }
}

/**
 * Closes with the reason depending on [cause] or normally if the [cause] is `null`.
 */
@Deprecated(
    "Close with reason or terminate instead.",
    level = DeprecationLevel.ERROR
)
public suspend fun WebSocketSession.close(cause: Throwable?) {
    if (cause == null) {
        close()
    } else {
        closeExceptionally(cause)
    }
}

/**
 * Closes a session with normal or error close reason, depending on whether [cause] is cancellation or not.
 */
public suspend fun WebSocketSession.closeExceptionally(cause: Throwable) {
    val reason = when (cause) {
        is CancellationException -> CloseReason(CloseReason.Codes.NORMAL, "")
        else -> CloseReason(CloseReason.Codes.INTERNAL_ERROR, cause.toString())
    }

    close(reason)
}
