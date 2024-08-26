/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.ktor.websocket.serialization.*

/**
 * Represents a server-side web socket session
 */
public interface WebSocketServerSession : WebSocketSession {
    /**
     * Associated received [call] that originating this session
     */
    public val call: ApplicationCall
}

/**
 * Represents a server-side web socket session with all default implementations
 *
 * @see DefaultWebSocketSession
 */
public interface DefaultWebSocketServerSession : DefaultWebSocketSession, WebSocketServerSession

/**
 * An application that started this web socket session
 */
public val WebSocketServerSession.application: Application get() = call.application

/**
 * Converter for web socket session
 */
public val WebSocketServerSession.converter: WebsocketContentConverter?
    get() = application.plugin(WebSockets).contentConverter

/**
 * Serializes [data] to a frame and enqueues this frame.
 * May suspend if the outgoing queue is full.
 * If the outgoing channel is already closed, throws an exception, so it is impossible to transfer any message.
 * Frames sent after a Close frame are silently ignored.
 * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
 *
 * @param typeInfo Type info of [T]. Can be retrieved with [typeInfo] function.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 */
@OptIn(InternalAPI::class)
public suspend fun WebSocketServerSession.sendSerialized(data: Any?, typeInfo: TypeInfo) {
    val converter = converter
        ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    sendSerializedBase(data, typeInfo, converter, call.request.headers.suitableCharset())
}

/**
 * Serializes [data] to a frame and enqueues this frame.
 * May suspend if the outgoing queue is full.
 * If the outgoing channel is already closed, throws an exception, so it is impossible to transfer any message.
 * Frames sent after a Close frame are silently ignored.
 * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 */
public suspend inline fun <reified T> WebSocketServerSession.sendSerialized(data: T) {
    sendSerialized(data, typeInfo<T>())
}

/**
 * Dequeues a frame and deserializes it to the type [T] using WebSocket content converter.
 * May throw an exception [WebsocketDeserializeException] if the converter can't deserialize frame data to type [T].
 * May throw [WebsocketDeserializeException] if the received frame type is not [Frame.Text] or [Frame.Binary].
 * In this case, [WebsocketDeserializeException.frame] contains the received frame.
 * May throw [ClosedReceiveChannelException] if a channel was closed
 *
 * @param typeInfo Type info of [T]. Can be retrieved with [typeInfo] function.

 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 * @throws WebsocketDeserializeException if the received frame can't be deserialized to type [T]
 */
@OptIn(InternalAPI::class)
public suspend fun <T> WebSocketServerSession.receiveDeserialized(typeInfo: TypeInfo): T {
    val converter = converter
        ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    @Suppress("UNCHECKED_CAST")
    return receiveDeserializedBase(typeInfo, converter, call.request.headers.suitableCharset()) as T
}

public suspend inline fun <reified T> WebSocketServerSession.receiveDeserialized(): T =
    receiveDeserialized(typeInfo<T>())

internal fun WebSocketSession.toServerSession(call: ApplicationCall): WebSocketServerSession =
    DelegatedWebSocketServerSession(call, this)

internal fun DefaultWebSocketSession.toServerSession(call: ApplicationCall): DefaultWebSocketServerSession =
    DelegatedDefaultWebSocketServerSession(call, this)

private class DelegatedWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: WebSocketSession
) : WebSocketServerSession, WebSocketSession by delegate

private class DelegatedDefaultWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: DefaultWebSocketSession
) : DefaultWebSocketServerSession, DefaultWebSocketSession by delegate
