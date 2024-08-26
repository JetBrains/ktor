/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions.serialization

import io.ktor.server.sessions.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

/**
 * Returns a [SessionSerializer] based on `kotlinx.serialization` library.
 */
@Suppress("FunctionName")
public inline fun <reified T : Any> KotlinxSessionSerializer(
    format: StringFormat
): SessionSerializer<T> {
    return KotlinxSessionSerializer(serializer(), format)
}

/**
 * Returns a [SessionSerializer] based on `kotlinx.serialization` library.
 */
@Suppress("FunctionName")
public fun <T : Any> KotlinxSessionSerializer(
    serializer: KSerializer<T>,
    format: StringFormat
): SessionSerializer<T> {
    return KotlinxSessionSerializer(format, serializer)
}

/**
 * Returns a [SessionSerializer] based on `kotlinx.serialization` library
 * that is backward compatible with previous default serializer.
 * In general, it's discouraged to use this format, and it's recommended to migrate your sessions to another format,
 * such as JSON
 */
@Suppress("FunctionName")
public inline fun <reified T : Any> KotlinxBackwardCompatibleSessionSerializer(
    serializersModule: SerializersModule = EmptySerializersModule()
): SessionSerializer<T> {
    return KotlinxBackwardCompatibleSessionSerializer(serializer(), serializersModule)
}

/**
 * Returns a [SessionSerializer] based on `kotlinx.serialization` library
 * that is backward compatible with previous default serializer.
 * In general, it's discouraged to use this format, and it's recommended to migrate your sessions to another format,
 * such as JSON
 */
@Suppress("FunctionName")
public fun <T : Any> KotlinxBackwardCompatibleSessionSerializer(
    serializer: KSerializer<T>,
    serializersModule: SerializersModule = EmptySerializersModule()
): SessionSerializer<T> {
    return KotlinxSessionSerializer(serializer, SessionsBackwardCompatibleFormat(serializersModule))
}

private class KotlinxSessionSerializer<T : Any>(
    private val format: StringFormat,
    private val serializer: KSerializer<T>
) : SessionSerializer<T> {
    override fun serialize(session: T): String = format.encodeToString(serializer, session)
    override fun deserialize(text: String): T = format.decodeFromString(serializer, text)
}

internal class SessionsBackwardCompatibleFormat(override val serializersModule: SerializersModule) : StringFormat {

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val decoder = SessionsBackwardCompatibleDecoder(serializersModule, string)
        return decoder.decodeSerializableValue(deserializer)
    }

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val encoder = SessionsBackwardCompatibleEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return encoder.result()
    }
}
