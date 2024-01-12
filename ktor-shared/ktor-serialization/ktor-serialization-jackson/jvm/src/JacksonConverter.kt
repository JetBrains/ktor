/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.serialization.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import kotlin.text.*

/**
 * A content converter that uses [Jackson]
 *
 * @param objectMapper a configured instance of [ObjectMapper]
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 */
public class JacksonConverter(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val streamRequestBody: Boolean = true
) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        if (!streamRequestBody && typeInfo.type != Flow::class) {
            return TextContent(
                objectMapper.writeValueAsString(value),
                contentType.withCharsetIfNeeded(charset)
            )
        }
        return OutputStreamContent(
            {
                /*
                Jackson internally does special casing on UTF-8, presumably for performance reasons.
                Thus, we pass an InputStream instead of a Writer to let Jackson do its thing.
                 */
                if (charset == Charsets.UTF_8) {
                    // specific behavior for kotlinx.coroutines.flow.Flow
                    if (typeInfo.type == Flow::class) {
                        // emit asynchronous values in OutputStream without pretty print
                        serializeJson((value as Flow<*>), this)
                    } else {
                        objectMapper.writeValue(this, value)
                    }
                } else {
                    // For other charsets, we use a Writer
                    val writer = this.writer(charset = charset)

                    // specific behavior for kotlinx.coroutines.flow.Flow
                    if (typeInfo.type == Flow::class) {
                        // emit asynchronous values in Writer without pretty print
                        serializeJson((value as Flow<*>), writer)
                    } else {
                        objectMapper.writeValue(writer, value)
                    }
                }
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
                objectMapper.readValue(reader, objectMapper.constructType(typeInfo.reifiedType))
            }
        } catch (cause: Exception) {
            val convertException = JsonConvertException("Illegal json parameter found: ${cause.message}", cause)

            when (cause) {
                is JsonParseException -> throw convertException
                is JsonMappingException -> throw convertException
                else -> throw cause
            }
        }
    }

    private companion object {
        private const val beginArrayCharCode = '['.code
        private const val endArrayCharCode = ']'.code
        private const val objectSeparator = ','.code
    }

    private val jfactory by lazy { JsonFactory() }

    private suspend fun <T> serializeJson(flow: Flow<T>, outputStream: OutputStream) {
        // cannot use ObjectMapper write to Stream because it flushes the OutputStream on each write
        val jGenerator = jfactory.createGenerator(outputStream, JsonEncoding.UTF8)
        serialize(flow, jGenerator, outputStream) { outputStream.write(it) }
    }

    private suspend fun <T> serializeJson(flow: Flow<T>, writer: Writer) {
        // cannot use ObjectMapper write to Stream because it flushes the OutputStream on each write
        val jGenerator = jfactory.createGenerator(writer)
        serialize(flow, jGenerator, writer) { writer.write(it) }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <Stream : Flushable, T> serialize(
        flow: Flow<T>,
        jGenerator: JsonGenerator,
        stream: Stream,
        writeByte: Stream.(Int) -> Unit
    ) {
        jGenerator.setup()
        stream.writeByte(beginArrayCharCode)
        flow.collectIndexed { index, value ->
            if (index > 0) {
                stream.writeByte(objectSeparator)
            }
            jGenerator.writeObject(value)
            stream.flush()
        }
        stream.writeByte(endArrayCharCode)
        stream.flush()
    }

    private fun JsonGenerator.setup() {
        configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
        prettyPrinter = MinimalPrettyPrinter("") // avoid single space between items
        codec = objectMapper
    }
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using Jackson.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 * @param contentType the content type to send with request
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 * @param block a configuration block for [ObjectMapper]
 */
public fun Configuration.jackson(
    contentType: ContentType = ContentType.Application.Json,
    streamRequestBody: Boolean = true,
    block: ObjectMapper.() -> Unit = {}
) {
    val mapper = ObjectMapper()
    mapper.apply {
        setDefaultPrettyPrinter(
            DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            }
        )
    }
    mapper.apply(block)
    mapper.registerKotlinModule()
    val converter = JacksonConverter(mapper, streamRequestBody)
    register(contentType, converter)
}
