/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.sse.*
import kotlinx.coroutines.*

/**
 * Represents a server-side server-sent events session.
 * An [ServerSSESession] allows the server to send [ServerSentEvent] to the client over a single HTTP connection.
 *
 * @see [SSE]
 */
public interface ServerSSESession : CoroutineScope {
    /**
     * Associated received [call] that originating this session.
     */
    public val call: ApplicationCall

    /**
     * Sends a [ServerSentEvent] to the client.
     */
    public suspend fun send(event: ServerSentEvent)

    /**
     * Creates and sends a [ServerSentEvent] to the client.
     *
     *  @param data data field of the event.
     *  @param event string identifying the type of event.
     *  @param id event ID.
     *  @param retry reconnection time, in milliseconds to wait before reconnecting.
     *  @param comments comment lines starting with a ':' character.
     */
    public suspend fun send(
        data: String? = null,
        event: String? = null,
        id: String? = null,
        retry: Long? = null,
        comments: String? = null
    ) {
        send(ServerSentEvent(data, event, id, retry, comments))
    }

    /**
     * Closes the [ServerSSESession], terminating the connection with the client.
     * Once this method is called, the SSE session is closed and no further events can be sent.
     * You don't need to call this method as it is called automatically when all the send operations are completed.
     *
     * It's important to note that closing the session using this method does not send a termination event
     * to the client. If you wish to send a specific event to signify the end of the SSE stream
     * before closing the session, you can use the [send] function for it.
     */
    public suspend fun close()
}
