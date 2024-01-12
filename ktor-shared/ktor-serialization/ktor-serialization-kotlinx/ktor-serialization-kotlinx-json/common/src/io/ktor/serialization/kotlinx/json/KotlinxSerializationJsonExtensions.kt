/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

/**
 * Adds special handling for receiving [Sequence] and sending [Flow] bodies for the Json format.
 */
public class KotlinxSerializationJsonExtensionProvider : KotlinxSerializationExtensionProvider {
    override fun extension(format: SerialFormat): KotlinxSerializationExtension? {
        if (format !is Json) return null
        return KotlinxSerializationJsonExtensions(format)
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
internal class KotlinxSerializationJsonExtensions(private val format: Json) : KotlinxSerializationExtension {

    private val jsonArraySymbolsMap = mutableMapOf<Charset, JsonArraySymbols>()

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (charset != Charsets.UTF_8 || typeInfo.type != Flow::class) return null

        val elementTypeInfo = typeInfo.argumentTypeInfo()
        val serializer = format.serializersModule.serializerForTypeInfo(elementTypeInfo)
        return ChannelWriterContent(
            {
                // emit asynchronous values in OutputStream without pretty print
                @Suppress("UNCHECKED_CAST")
                (value as Flow<*>).serialize(
                    serializer as KSerializer<Any?>,
                    charset,
                    this
                )
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        // kotlinx.serialization decodeFromStream only supports UTF-8
        if (charset != Charsets.UTF_8 || typeInfo.type != Sequence::class) return null

        try {
            return deserializeSequence(format, content, typeInfo)
        } catch (cause: Throwable) {
            throw JsonConvertException("Illegal input: ${cause.message}", cause)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <T> Flow<T>.serialize(
        serializer: KSerializer<T>,
        charset: Charset,
        channel: ByteWriteChannel
    ) {
        val jsonArraySymbols = jsonArraySymbolsMap.getOrPut(charset) { JsonArraySymbols(charset) }

        channel.writeFully(jsonArraySymbols.beginArray)
        collectIndexed { index, value ->
            if (index > 0) {
                channel.writeFully(jsonArraySymbols.objectSeparator)
            }
            val string = format.encodeToString(serializer, value)
            channel.writeFully(string.toByteArray(charset))
            channel.flush()
        }
        channel.writeFully(jsonArraySymbols.endArray)
    }
}

private class JsonArraySymbols(charset: Charset) {
    val beginArray = "[".toByteArray(charset)
    val endArray = "]".toByteArray(charset)
    val objectSeparator = ",".toByteArray(charset)
}

internal fun TypeInfo.argumentTypeInfo(): TypeInfo {
    val elementType = kotlinType!!.arguments[0].type!!
    return TypeInfo(
        elementType.classifier as KClass<*>,
        elementType.platformType,
        elementType
    )
}

internal expect suspend fun deserializeSequence(
    format: Json,
    content: ByteReadChannel,
    typeInfo: TypeInfo
): Sequence<Any?>?
