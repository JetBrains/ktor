/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket.serialization

import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*

/**
 * Serializes [data] to a frame and enqueues this frame.
 * May suspend if the [outgoing] queue is full.
 * If the [outgoing] channel is already closed, throws an exception, so it is impossible to transfer any message.
 * Frames sent after a Close frame are silently ignored.
 * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
 *
 * @param data The data to serialize
 * @param converter The WebSocket converter
 * @param charset Response charset
 */
@InternalAPI
public suspend inline fun <reified T> WebSocketSession.sendSerializedBase(
    data: Any?,
    converter: WebsocketContentConverter,
    charset: Charset
): Unit = sendSerializedBase(data, typeInfo<T>(), converter, charset)

/**
 * Serializes [data] to a frame and enqueues this frame.
 * May suspend if the [outgoing] queue is full.
 * If the [outgoing] channel is already closed, throws an exception, so it is impossible to transfer any message.
 * Frames sent after a Close frame are silently ignored.
 * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
 *
 * @param data The data to serialize
 * @param typeInfo Type info of [T]. Can be retrieved with [typeInfo] function.
 * @param converter The WebSocket converter
 * @param charset Response charset
 */
@InternalAPI
public suspend fun WebSocketSession.sendSerializedBase(
    data: Any?,
    typeInfo: TypeInfo,
    converter: WebsocketContentConverter,
    charset: Charset
) {
    val serializedData = converter.serialize(
        charset = charset,
        typeInfo = typeInfo,
        value = data
    )
    outgoing.send(serializedData)
}

/**
 * Dequeues a frame and deserializes it to the type [T] using [converter].
 * May throw [WebsocketDeserializeException] if the received frame type is not [Frame.Text] or [Frame.Binary].
 * In this case, [WebsocketDeserializeException.frame] contains the received frame.
 * May throw [ClosedReceiveChannelException] if a channel was closed
 *
 * @param converter The WebSocket converter
 * @param charset Response charset
 *
 * @returns A deserialized value or throws [WebsocketDeserializeException] if the [converter]
 * can't deserialize frame data to type [T]
 */
@InternalAPI
public suspend inline fun <reified T> WebSocketSession.receiveDeserializedBase(
    converter: WebsocketContentConverter,
    charset: Charset
): Any? = receiveDeserializedBase(typeInfo<T>(), converter, charset)

/**
 * Dequeues a frame and deserializes it to the type [T] using [converter].
 * May throw [WebsocketDeserializeException] if the received frame type is not [Frame.Text] or [Frame.Binary].
 * In this case, [WebsocketDeserializeException.frame] contains the received frame.
 * May throw [ClosedReceiveChannelException] if a channel was closed
 *
 * @param typeInfo Type info of [T]. Can be retrieved with [typeInfo] function.
 * @param converter The WebSocket converter
 * @param charset Response charset
 *
 * @returns A deserialized value or throws [WebsocketDeserializeException] if the [converter]
 * can't deserialize frame data to type [T]
 */
@InternalAPI
public suspend fun WebSocketSession.receiveDeserializedBase(
    typeInfo: TypeInfo,
    converter: WebsocketContentConverter,
    charset: Charset
): Any? {
    val frame = incoming.receive()

    if (!converter.isApplicable(frame)) {
        throw WebsocketDeserializeException(
            "Converter doesn't support frame type ${frame.frameType.name}",
            frame = frame
        )
    }

    val result = converter.deserialize(
        charset = charset,
        typeInfo = typeInfo,
        content = frame
    )

    when {
        typeInfo.type.isInstance(result) -> return result
        result == null -> {
            if (typeInfo.kotlinType?.isMarkedNullable == true) return null
            throw WebsocketDeserializeException("Frame has null content", frame = frame)
        }
    }

    throw WebsocketDeserializeException(
        "Can't deserialize value: expected value of type ${typeInfo.type.simpleName}," +
            " got ${result!!::class.simpleName}",
        frame = frame
    )
}
