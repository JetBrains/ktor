/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.serialization.*

/**
 * Creates a converter for WebSocket serializing with the specified string [format] and
 * [defaultCharset] (optional, usually it is UTF-8).
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
public class KotlinxWebsocketSerializationConverter(
    private val format: SerialFormat,
) : WebsocketContentConverter {

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
        val serializer = try {
            format.serializersModule.serializerForTypeInfo(typeInfo)
        } catch (cause: SerializationException) {
            guessSerializer(value, format.serializersModule)
        }
        return serializeContent(serializer, format, value)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        val serializer = format.serializersModule.serializerForTypeInfo(typeInfo)

        return when (format) {
            is StringFormat -> {
                if (content is Frame.Text) {
                    format.decodeFromString(serializer, content.readText())
                } else {
                    throw WebsocketDeserializeException(
                        "Unsupported format $format for ${content.frameType.name}",
                        frame = content
                    )
                }
            }
            is BinaryFormat -> {
                if (content is Frame.Binary) {
                    format.decodeFromByteArray(serializer, content.readBytes())
                } else {
                    throw WebsocketDeserializeException(
                        "Unsupported format $format for ${content.frameType.name}",
                        frame = content
                    )
                }
            }
            else -> {
                error("Unsupported format $format")
            }
        }
    }

    override fun isApplicable(frame: Frame): Boolean {
        return frame is Frame.Text || frame is Frame.Binary
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any?
    ): Frame {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any?>, value)
                Frame.Text(content)
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any?>, value)
                Frame.Binary(true, content)
            }
            else -> error("Unsupported format $format")
        }
    }
}
