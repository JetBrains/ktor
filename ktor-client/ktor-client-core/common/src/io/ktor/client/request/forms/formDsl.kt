/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlin.contracts.*

/**
 * A multipart form item. Use it to build a form in client.
 *
 * @param key multipart name
 * @param value content, could be [String], [Number], [ByteArray], [ByteReadPacket] or [InputProvider]
 * @param headers part headers, note that some servers may fail if an unknown header provided
 */
public data class FormPart<T : Any>(val key: String, val value: T, val headers: Headers = Headers.Empty)

/**
 * Builds a multipart form from [values].
 *
 * Example: [Upload a file](https://ktor.io/docs/request.html#upload_file).
 */

public fun formData(vararg values: FormPart<*>): List<PartData> {
    val result = mutableListOf<PartData>()

    values.forEach { (key, value, headers) ->
        val partHeaders = HeadersBuilder().apply {
            append(HttpHeaders.ContentDisposition, "form-data; name=${key.escapeIfNeeded()}")
            appendAll(headers)
        }

        val part = when (value) {
            is String -> PartData.FormItem(value, {}, partHeaders.build())
            is Number -> PartData.FormItem(value.toString(), {}, partHeaders.build())
            is Boolean -> PartData.FormItem(value.toString(), {}, partHeaders.build())
            is ByteArray -> {
                partHeaders.append(HttpHeaders.ContentLength, value.size.toString())
                PartData.BinaryItem({ ByteReadPacket(value) }, {}, partHeaders.build())
            }
            is Source -> {
                partHeaders.append(HttpHeaders.ContentLength, value.remaining.toString())
                PartData.BinaryItem({ value.copy() }, { value.close() }, partHeaders.build())
            }
            is InputProvider -> {
                val size = value.size
                if (size != null) {
                    partHeaders.append(HttpHeaders.ContentLength, size.toString())
                }
                PartData.BinaryItem(value.block, {}, partHeaders.build())
            }
            is ChannelProvider -> {
                val size = value.size
                if (size != null) {
                    partHeaders.append(HttpHeaders.ContentLength, size.toString())
                }
                PartData.BinaryChannelItem(value.block, partHeaders.build())
            }
            else -> error("Unknown form content type: $value")
        }

        result += part
    }

    return result
}

/**
 * Build multipart form using [block] function.
 */
public fun formData(block: FormBuilder.() -> Unit): List<PartData> =
    formData(*FormBuilder().apply(block).build().toTypedArray())

/**
 * A form builder type used in the [formData] builder function.
 */
public class FormBuilder internal constructor() {
    private val parts = mutableListOf<FormPart<*>>()

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    @InternalAPI
    public fun <T : Any> append(key: String, value: T, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: String, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: Number, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: Boolean, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: ByteArray, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: InputProvider, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[InputProvider(block)] with optional [headers].
     */

    public fun appendInput(key: String, headers: Headers = Headers.Empty, size: Long? = null, block: () -> Input) {
        parts += FormPart(key, InputProvider(size, block), headers)
    }

    /**
     * Appends a pair [key]:[value] with optional [headers].
     */
    public fun append(key: String, value: Source, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a pair [key]:[values] with optional [headers].
     */
    public fun append(key: String, values: Iterable<String>, headers: Headers = Headers.Empty) {
        require(key.endsWith("[]")) {
            "Array parameter must be suffixed with square brackets ie `$key[]`"
        }
        values.forEach { value ->
            parts += FormPart(key, value, headers)
        }
    }

    /**
     * Appends a pair [key]:[values] with optional [headers].
     */
    public fun append(key: String, values: Array<String>, headers: Headers = Headers.Empty) {
        return append(key, values.asIterable(), headers)
    }

    /**
     * Appends a pair [key]:[ChannelProvider] with optional [headers].
     */
    public fun append(key: String, value: ChannelProvider, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Appends a form [part].
     */
    public fun <T : Any> append(part: FormPart<T>) {
        parts += part
    }

    internal fun build(): List<FormPart<*>> = parts
}

/**
 * Appends a form part with the specified [key] using [bodyBuilder] for its body.
 */

@OptIn(ExperimentalContracts::class)
public inline fun FormBuilder.append(
    key: String,
    headers: Headers = Headers.Empty,
    size: Long? = null,
    crossinline bodyBuilder: Sink.() -> Unit
) {
    contract {
        callsInPlace(bodyBuilder, InvocationKind.EXACTLY_ONCE)
    }
    append(FormPart(key, InputProvider(size) { buildPacket { bodyBuilder() } }, headers))
}

/**
 * A reusable [Input] form entry.
 *
 * @property size estimate for data produced by the block or `null` if no size estimation known
 * @param block: content generator
 */

public class InputProvider(public val size: Long? = null, public val block: () -> Input)

/**
 * Supplies a new [ByteReadChannel].
 * @property size is total number of bytes that can be read from [ByteReadChannel] or `null` if [size] is unknown
 * @param block returns a new [ByteReadChannel]
 */
public class ChannelProvider(public val size: Long? = null, public val block: () -> ByteReadChannel)

/**
 * Appends a form part with the specified [key], [filename], and optional [contentType] using [bodyBuilder] for its body.
 */

@OptIn(ExperimentalContracts::class)
public fun FormBuilder.append(
    key: String,
    filename: String,
    contentType: ContentType? = null,
    size: Long? = null,
    bodyBuilder: Sink.() -> Unit
) {
    contract {
        callsInPlace(bodyBuilder, InvocationKind.EXACTLY_ONCE)
    }

    val headersBuilder = HeadersBuilder()
    headersBuilder[HttpHeaders.ContentDisposition] = "filename=${filename.escapeIfNeeded()}"
    contentType?.run { headersBuilder[HttpHeaders.ContentType] = this.toString() }
    val headers = headersBuilder.build()

    append(key, headers, size, bodyBuilder)
}
