/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
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
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.serialization.jackson.JacksonConverter)
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
                    val jGenerator = objectMapper.factory.createGenerator(this, JsonEncoding.UTF8)

                    if (typeInfo.type == Flow::class) {
                        serialize(value as Flow<*>, jGenerator, this)
                    } else {
                        objectMapper.writeValue(jGenerator, value)
                    }
                } else {
                    // For other charsets, we use a Writer
                    val writer = this.writer(charset = charset)
                    val jGenerator = objectMapper.factory.createGenerator(writer)

                    if (typeInfo.type == Flow::class) {
                        serialize(value as Flow<*>, jGenerator, writer)
                    } else {
                        objectMapper.writeValue(jGenerator, value)
                    }
                }
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        try {
            return withContext(Dispatchers.IO) {
                val type = objectMapper.constructType(typeInfo.reifiedType)
                val objectReader = objectMapper.readerFor(type)

                // Jackson handles decoding of Unicode charsets automatically, no need to create a reader.
                // Additionally, a byte-based source is required for binary format (Smile).
                if (isUnicode(charset)) {
                    objectReader.readValue(content.toInputStream())
                } else {
                    val reader = content.toInputStream().reader(charset)
                    objectReader.readValue(reader)
                }
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
        private val jacksonEncodings = buildSet<String> {
            JsonEncoding.entries.forEach { add(it.javaName) }
            add("US-ASCII") // Jackson automatically unescapes Unicode characters
        }

        private fun isUnicode(charset: Charset): Boolean {
            return jacksonEncodings.contains(charset.name()) ||
                charset == Charsets.UTF_16 ||
                charset == Charsets.UTF_32
        }
    }

    /**
     * Cannot use ObjectMapper write to Stream because it flushes the OutputStream on each write
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <Stream : Flushable, T> serialize(
        flow: Flow<T>,
        jGenerator: JsonGenerator,
        stream: Stream
    ) {
        jGenerator.setup()
        jGenerator.writeStartArray()

        flow.collect { value ->
            jGenerator.writeObject(value)
            stream.flush()
        }

        jGenerator.writeEndArray()
        jGenerator.flush()
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
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.serialization.jackson.jackson)
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
