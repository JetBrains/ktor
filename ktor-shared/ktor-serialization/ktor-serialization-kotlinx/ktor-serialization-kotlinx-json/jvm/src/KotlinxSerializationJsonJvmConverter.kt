/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*
import kotlin.reflect.*
import kotlin.text.*

@OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
public class KotlinxSerializationJsonJvmConverter(private val json: Json) :
    AbstractKotlinxSerializationConverter(json) {

    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        // kotlinx.serialization internally does special casing on UTF-8, presumably for performance reasons
        if (charset == Charsets.UTF_8) {
            OutputStreamContent(
                {
                    // specific behavior for kotlinx.coroutines.flow.Flow : emit asynchronous values in OutputStream
                    if (typeInfo.type == Flow::class) {
                        (value as Flow<*>).serializeJson(this)
                    } else {
                        // non flow content
                        outputStreamSerializationBase.serialize(
                            OutputStreamSerializationParameters(
                                json,
                                value,
                                typeInfo,
                                Charsets.UTF_8,
                                this
                            )
                        )
                    }
                },
                contentType.withCharsetIfNeeded(Charsets.UTF_8)
            )
        }
        // else fallback to common KotlinxSerializationConverter
        return fallbackConverter.serializeNullable(contentType, charset, typeInfo, value)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        // kotlinx.serialization decodeFromStream only supports UTF-8
        if (charset == Charsets.UTF_8) {
            try {
                return withContext(Dispatchers.IO) {
                    val inputStream = content.toInputStream()
                    if (typeInfo.type == Sequence::class) {
                        // build TypeInfo of the generic Sequence, for Sequence<T> it means T
                        val elementType = typeInfo.kotlinType!!.arguments[0].type!!
                        val elementTypeInfo = typeInfoImpl(
                            elementType.platformType,
                            elementType.classifier as KClass<*>,
                            elementType
                        )
                        json.decodeToSequence(inputStream, serializerFromTypeInfo(elementTypeInfo))
                    } else {
                        val serializer = serializerFromTypeInfo(typeInfo)
                        json.decodeFromStream(serializer, inputStream)
                    }
                }
            } catch (cause: Throwable) {
                throw JsonConvertException("Illegal input", cause)
            }
        }
        // fallback to common KotlinxSerializationConverter
        return fallbackConverter.deserialize(charset, typeInfo, content)
    }

    private companion object {
        private const val beginArrayCharCode = '['.code
        private const val endArrayCharCode = ']'.code
        private const val objectSeparator = ','.code
    }

    /**
     * Guaranteed to be called inside a [Dispatchers.IO] context, see [OutputStreamContent]
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend inline fun <reified T> Flow<T>.serializeJson(outputStream: OutputStream) {
        outputStream.write(beginArrayCharCode)
        collectIndexed { index, value ->
            if (index > 0) {
                outputStream.write(objectSeparator)
            }
            outputStreamSerializationBase.serialize(
                OutputStreamSerializationParameters(
                    json,
                    value as Any,
                    typeInfo<T>(),
                    Charsets.UTF_8,
                    outputStream
                )
            )
        }
        outputStream.write(endArrayCharCode)
    }

    private val outputStreamSerializationBase by lazy {
        object : KotlinxSerializationBase<Unit>(json) {
            override suspend fun serializeContent(parameters: SerializationParameters) {
                if (parameters !is OutputStreamSerializationParameters) {
                    error(
                        "parameters type is ${parameters::class.simpleName}," +
                            " but expected ${OutputStreamSerializationParameters::class.simpleName}"
                    )
                }
                serializeContent(
                    parameters.serializer,
                    parameters.value,
                    parameters.outputStream
                )
            }
        }
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        value: Any?,
        outputStream: OutputStream
    ) {
        @Suppress("UNCHECKED_CAST")
        json.encodeToStream(serializer as KSerializer<Any?>, value, outputStream)
    }

    private val fallbackConverter by lazy {
        KotlinxSerializationConverter(json)
    }
}

@OptIn(InternalAPI::class)
internal class OutputStreamSerializationParameters(
    override val format: SerialFormat,
    override val value: Any?,
    override val typeInfo: TypeInfo,
    override val charset: Charset,
    internal val outputStream: OutputStream
) : SerializationParameters(format, value, typeInfo, charset)
