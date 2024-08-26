/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * An [HttpClient]'s response, a second part of [HttpClientCall].
 *
 * Learn more from [Receiving responses](https://ktor.io/docs/response.html).
 */
public abstract class HttpResponse : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    public abstract val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    public abstract val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    public abstract val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    public abstract val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    public abstract val responseTime: GMTDate

    /**
     * Provides a raw [ByteReadChannel] to the response content as it is read from the network.
     * This content can be still compressed or encoded.
     *
     * This content doesn't go through any interceptors from [HttpResponsePipeline].
     *
     * If you need to read the content as decoded bytes, use the [bodyAsChannel] method instead.
     *
     * This property produces a new channel every time it's accessed.
     */
    @InternalAPI
    public abstract val rawContent: ByteReadChannel

    override fun toString(): String = "HttpResponse[${request.url}, $status]"
}

/**
 * Provides a raw [ByteReadChannel] to the response content as it was read from the network.
 * This content can be still compressed or encoded.
 *
 * This content doesn't go through any interceptors from [HttpResponsePipeline].
 *
 * If you need to read the content as decoded bytes, use the [bodyAsChannel] method instead.
 */
@InternalAPI
@Deprecated(
    "This method was renamed to readRawBytes() to reflect what it does.",
    ReplaceWith("readRawBytes()")
)
public val HttpResponse.content: ByteReadChannel get() = rawContent

/**
 * Gets [HttpRequest] associated with this response.
 */
public val HttpResponse.request: HttpRequest get() = call.request

@InternalAPI
@PublishedApi
internal fun HttpResponse.complete() {
    val job = coroutineContext[Job]!! as CompletableJob
    job.complete()
}

/**
 * Reads the [HttpResponse.rawContent] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the `Content-Type` response.
 * If no charset specified either as parameter or as part of the response,
 * [io.ktor.client.plugins.HttpPlainText] settings will be used.
 *
 * Note that [fallbackCharset] parameter will be ignored if the response already has a charset.
 *  So it just acts as a fallback, honoring the server preference.
 */
public suspend fun HttpResponse.bodyAsText(fallbackCharset: Charset = Charsets.UTF_8): String {
    val originCharset = charset() ?: fallbackCharset
    val decoder = originCharset.newDecoder()
    val input = body<Source>()

    return decoder.decode(input)
}

/**
 * Reads the [HttpResponse.rawContent] as a [ByteReadChannel].
 */
public suspend fun HttpResponse.bodyAsChannel(): ByteReadChannel = body()

/**
 * Reads the response body as a byte array. Note that all plugins will be applied to the response body, which may be
 * decompressed or decoded.
 *
 * If you need to read the raw payload of the HTTP response as a byte array, use the [rawContent] property instead.
 */
public suspend fun HttpResponse.bodyAsBytes(): ByteArray = body()
