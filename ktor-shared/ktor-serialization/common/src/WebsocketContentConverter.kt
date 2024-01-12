/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*

/**
 * A custom content converter that could be used in the [WebSockets] plugin
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of the content converter is a JSON converter that provides
 * both serialization and deserialization
 */
public interface WebsocketContentConverter {
    /**
     * Serializes a [value] to a WebSocket [Frame].
     * This function could throw `WebsocketConverterNotFoundException` if the value is not suitable for conversion
     *
     * @param charset response charset
     * @param typeInfo response body typeInfo
     * @param value to be converted
     *
     * @return a converted [Frame] value, or null if [value] isn't suitable for this converter
     */
    public suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): Frame = serialize(charset, typeInfo, value!!)

    /**
     * Deserializes [content] to the value of type [typeInfo]
     *
     * @return a converted value (deserialized) or throws `WebsocketConverterNotFoundException` if the context's
     * subject is not suitable for this converter
     */
    public suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any?

    /**
     * Checks if the content converter can deserialize a [frame]
     *
     * @param frame a WebSocket frame
     *
     * @return true if the content converter can deserialize a [frame] type or false if a type of [frame]
     * is not supported by the converter
     */
    public fun isApplicable(frame: Frame): Boolean
}

/**
 * Serializes a [value] to a WebSocket [Frame].
 * This function could throw `WebsocketConverterNotFoundException` if the value is not suitable for conversion
 *
 * @param charset response charset
 * @param value to be converted
 *
 * @return a converted [OutgoingContent] value, or null if [value] isn't suitable for this converter
 */
public suspend inline fun <reified T> WebsocketContentConverter.serialize(
    value: T,
    charset: Charset = Charsets.UTF_8
): Frame = serialize(charset, typeInfo<T>(), value)

/**
 * Deserializes [content] to the value of type [T]
 *
 * @return a converted value (deserialized) or throws `WebsocketConverterNotFoundException` if the context's
 * subject is not suitable for this converter
 */
public suspend inline fun <reified T> WebsocketContentConverter.deserialize(
    content: Frame,
    charset: Charset = Charsets.UTF_8
): T = deserialize(charset, typeInfo<T>(), content) as T
