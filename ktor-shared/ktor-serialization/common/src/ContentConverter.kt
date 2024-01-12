/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.*

/**
 * A custom content converter that could be registered in [ContentNegotiation] plugin for any particular content type
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of content converter is a JSON content converter that provides both
 * serialization and deserialization
 *
 * Implementations must override at least one of [serialize] or [serialize] methods.
 */
public interface ContentConverter {

    /**
     * Serializes a [value] to the specified [contentType] to a [OutgoingContent].
     * This function could ignore value if it is not suitable for conversion and return `null` so in this case
     * other registered converters could be tried or this function could be invoked with other content types
     * it the converted has been registered multiple times with different content types.
     *
     * @param charset response charset
     * @param typeInfo response body typeInfo
     * @param contentType to which this data converter has been registered and that matches the client's [Accept] header
     * @param value to be converted
     *
     * @return a converted [OutgoingContent] value, or null if [value] isn't suitable for this converter
     */
    public suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent?

    /**
     * Deserializes [content] to the value of type [typeInfo]
     *
     * @return a converted value (deserialized) or `null` if the context's subject is not suitable for this converter
     */
    public suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any?
}

/**
 * Detect suitable charset for an application call by `Accept` header or fallback to [defaultCharset]
 */
public fun Headers.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset =
    suitableCharsetOrNull(defaultCharset) ?: defaultCharset

/**
 * Detect suitable charset for an application call by `Accept` header or fallback to null
 */
public fun Headers.suitableCharsetOrNull(defaultCharset: Charset = Charsets.UTF_8): Charset? {
    for ((charset, _) in parseAndSortHeader(get(HttpHeaders.AcceptCharset))) when {
        charset == "*" -> return defaultCharset
        Charsets.isSupported(charset) -> return Charsets.forName(charset)
    }
    return null
}

/**
 * Configuration for client and server `ContentNegotiation` plugin
 */
public interface Configuration {

    public fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit = {}
    )
}

@InternalAPI
public suspend fun List<ContentConverter>.deserialize(
    body: ByteReadChannel,
    typeInfo: TypeInfo,
    charset: Charset
): Any {
    // Pick the first one that can convert the subject successfully.
    // The result can be null if
    // 1. there is no suitable converter
    // 2. result of deserialization is null
    // We can differentiate these cases by checking if body was consumed or not
    val result = asFlow()
        .map { converter -> converter.deserialize(charset = charset, typeInfo = typeInfo, content = body) }
        .firstOrNull { it != null || body.isClosedForRead }

    return when {
        result != null -> result
        !body.isClosedForRead -> body
        typeInfo.kotlinType?.isMarkedNullable == true -> NullBody
        else -> throw ContentConvertException("No suitable converter found for $typeInfo")
    }
}
