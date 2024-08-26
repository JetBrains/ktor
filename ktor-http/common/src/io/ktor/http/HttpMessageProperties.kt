/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.http

import io.ktor.utils.io.charsets.*

/**
 * Set `Content-Type` header.
 */
public fun HttpMessageBuilder.contentType(type: ContentType): Unit =
    headers.set(HttpHeaders.ContentType, type.toString())

/**
 * Append `Max-Age` header value.
 */
public fun HttpMessageBuilder.maxAge(seconds: Int): Unit = headers.append(HttpHeaders.CacheControl, "max-age=$seconds")

/**
 * Set `If-None-Match` header value.
 */
public fun HttpMessageBuilder.ifNoneMatch(value: String): Unit = headers.set(HttpHeaders.IfNoneMatch, value)

/**
 * Set `User-Agent` header value.
 */
public fun HttpMessageBuilder.userAgent(content: String): Unit = headers.set(HttpHeaders.UserAgent, content)

/**
 * Parse `Content-Type` header value.
 */
public fun HttpMessageBuilder.contentType(): ContentType? =
    headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

/**
 * Parse charset from `Content-Type` header value.
 */
public fun HttpMessageBuilder.charset(): Charset? = contentType()?.charset()

/**
 * Parse `E-Tag` header value.
 */
public fun HttpMessageBuilder.etag(): String? = headers[HttpHeaders.ETag]

/**
 * Parse `Vary` header value.
 */
public fun HttpMessageBuilder.vary(): List<String>? = headers.getAll(HttpHeaders.Vary)?.flatMap { varyKeys ->
    varyKeys.split(",").map { it.trim() }
}

/**
 * Parse `Content-Length` header value.
 */
public fun HttpMessageBuilder.contentLength(): Long? = headers[HttpHeaders.ContentLength]?.toLong()

/**
 * Parse `Content-Type` header value.
 */
public fun HttpMessage.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

/**
 * Parse charset from `Content-Type` header value.
 */
public fun HttpMessage.charset(): Charset? = contentType()?.charset()

/**
 * Parse `E-Tag` header value.
 */
public fun HttpMessage.etag(): String? = headers[HttpHeaders.ETag]

/**
 * Parse `Vary` header value.
 */
public fun HttpMessage.vary(): List<String>? = headers.getAll(HttpHeaders.Vary)?.flatMap { varyKeys ->
    varyKeys.split(",").map { it.trim() }
}

/**
 * Parse `Content-Length` header value.
 */
public fun HttpMessage.contentLength(): Long? = headers[HttpHeaders.ContentLength]?.toLong()

/**
 * Parse `Set-Cookie` header value.
 */
public fun HttpMessage.setCookie(): List<Cookie> = headers.getAll(HttpHeaders.SetCookie)
    ?.flatMap { it.splitSetCookieHeader() }
    ?.map { parseServerSetCookieHeader(it) }
    ?: emptyList()

/**
 * Parse `Set-Cookie` header value.
 */
public fun HttpMessageBuilder.cookies(): List<Cookie> =
    headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: emptyList()

/**
 * Parse `CacheControl` header.
 */
public fun HttpMessage.cacheControl(): List<HeaderValue> = headers[HttpHeaders.CacheControl]?.let {
    parseHeaderValue(it)
} ?: emptyList()

internal fun String.splitSetCookieHeader(): List<String> {
    var comma = indexOf(',')

    if (comma == -1) {
        return listOf(this)
    }

    val result = mutableListOf<String>()
    var current = 0

    var equals = indexOf('=', comma)
    var semicolon = indexOf(';', comma)
    while (current < length && comma > 0) {
        if (equals < comma) {
            equals = indexOf('=', comma)
        }

        var nextComma = indexOf(',', comma + 1)
        while (nextComma in 0..<equals) {
            comma = nextComma
            nextComma = indexOf(',', nextComma + 1)
        }

        if (semicolon < comma) {
            semicolon = indexOf(';', comma)
        }

        // No more keys remaining.
        if (equals < 0) {
            result += substring(current)
            return result
        }

        // No ';' between ',' and '=' => We're on a header border.
        if (semicolon == -1 || semicolon > equals) {
            result += substring(current, comma)
            current = comma + 1
            // Update comma index at the end of loop.
        }

        // ',' in value, skip it and find next.
        comma = nextComma
    }

    // Add last chunk if no more ',' available.
    if (current < length) {
        result += substring(current)
    }

    return result
}
