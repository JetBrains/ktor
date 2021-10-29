/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.cio.websocket.*

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Client specific [WebSocketSession].
 */
public interface ClientWebSocketSession : WebSocketSession {
    /**
     * [HttpClientCall] associated with session.
     */
    public val call: HttpClientCall
}

/**
 * ClientSpecific [DefaultWebSocketSession].
 */
public class DefaultClientWebSocketSession(
    override val call: HttpClientCall,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate

internal class DelegatingClientWebSocketSession(
    override val call: HttpClientCall,
    session: WebSocketSession
) : ClientWebSocketSession, WebSocketSession by session

/**
 * Serializes [data] to frame and enqueue this frame, may suspend if outgoing queue is full. May throw an exception if outgoing channel is already
 * closed, so it is impossible to transfer any message. Frames that were sent after close frame could be silently
 * ignored. Please note that close frame could be sent automatically in reply to a peer close frame unless it is
 * raw websocket session.
 */
public suspend inline fun <reified T : Any> DefaultClientWebSocketSession.sendSerializedByWebsocketConverter(data: T) {
    /*
    val charset = call.request.headers.suitableCharset()
    val serializedData = call.client?.plugin(WebSockets)?.contentConverter?.serialize(
        charset = charset,
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    outgoing.send(
        Frame.Text(
            io.ktor.utils.io.core.String(
                serializedData.toByteArray(),
                charset = charset
            )
        )
    )

     */
}

/**
 * Dequeue frame and deserializes to type [T] using websocket content converter.
 * May throw an exception if converter can't deserialize frame data.
 * May throw [ClosedReceiveChannelException] if channel was closed
 * Please note that you don't need to use this method with raw websocket session.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 * @throws WebsocketDeserializeException if received frame can't be deserialized to type [T]
 */
public suspend inline fun <reified T : Any> DefaultClientWebSocketSession.receiveDeserialized(): T {
    TODO()
    /*
    val data = when (val frame = incoming.receive()) {
        is Frame.Text -> frame.data
        is Frame.Binary -> frame.data
        else -> throw WebsocketDeserializeException(
            "Frame type is not Frame.Text or Frame.Binary"
        )
    }

    val result = call.client?.plugin(WebSockets)?.contentConverter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = ByteReadChannel(data)
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    return if (result is T) result
    else throw WebsocketDeserializeException("Can't convert value from json")

     */
}
