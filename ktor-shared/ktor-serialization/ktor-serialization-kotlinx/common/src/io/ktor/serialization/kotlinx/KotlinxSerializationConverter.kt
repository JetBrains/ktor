/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlin.jvm.*

/**
 * Creates a converter serializing with the specified string [format]
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
public class KotlinxSerializationConverter(
    private val format: SerialFormat,
) : ContentConverter {

    private val extensions: List<KotlinxSerializationExtension> = extensions(format)

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, $format is not supported."
        }
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        val fromExtension = extensions.asFlow()
            .map { it.serialize(contentType, charset, typeInfo, value) }
            .firstOrNull { it != null }

        if (fromExtension != null) return fromExtension

        val serializer = try {
            format.serializersModule.serializerForTypeInfo(typeInfo)
        } catch (cause: SerializationException) {
            guessSerializer(value, format.serializersModule)
        }
        return serializeContent(serializer, format, value, contentType, charset)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val fromExtension = extensions.asFlow()
            .map { it.deserialize(charset, typeInfo, content) }
            .firstOrNull { it != null || content.isClosedForRead }
        if (extensions.isNotEmpty() && (fromExtension != null || content.isClosedForRead)) return fromExtension

        val serializer = format.serializersModule.serializerForTypeInfo(typeInfo)
        val contentPacket = content.readRemaining()

        try {
            return when (format) {
                is StringFormat -> format.decodeFromString(serializer, contentPacket.readText(charset))
                is BinaryFormat -> format.decodeFromByteArray(serializer, contentPacket.readBytes())
                else -> {
                    contentPacket.discard()
                    error("Unsupported format $format")
                }
            }
        } catch (cause: Throwable) {
            throw JsonConvertException("Illegal input: ${cause.message}", cause)
        }
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any?,
        contentType: ContentType,
        charset: Charset
    ): OutgoingContent.ByteArrayContent {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any?>, value)
                TextContent(content, contentType.withCharsetIfNeeded(charset))
            }

            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any?>, value)
                ByteArrayContent(content, contentType)
            }

            else -> error("Unsupported format $format")
        }
    }
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] plugin
 * with the specified [contentType] and binary [format] (such as CBOR, ProtoBuf)
 */
public fun Configuration.serialization(contentType: ContentType, format: BinaryFormat) {
    register(contentType, KotlinxSerializationConverter(format))
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] plugin
 * with the specified [contentType] and string [format] (such as Json)
 */
public fun Configuration.serialization(contentType: ContentType, format: StringFormat) {
    register(contentType, KotlinxSerializationConverter(format))
}
