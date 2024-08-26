/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.server.response

import io.ktor.http.*

/**
 * Appends a header with the specified [name] and [value] to a response.
 */
public fun ApplicationResponse.header(name: String, value: String): Unit = headers.append(name, value)

/**
 * Appends a header with the specified [name] and [value] to a response.
 */
public fun ApplicationResponse.header(name: String, value: Int): Unit = headers.append(name, value.toString())

/**
 * Appends a header with the specified [name] and [value] to a response.
 */
public fun ApplicationResponse.header(name: String, value: Long): Unit = headers.append(name, value.toString())

/**
 * Appends the `E-Tag` header with the specified [value] to a response.
 */
public fun ApplicationResponse.etag(value: String): Unit = header(HttpHeaders.ETag, value)

/**
 * Appends the `Cache-Control` header with the specified [value] to a response.
 */
public fun ApplicationResponse.cacheControl(value: CacheControl): Unit =
    header(HttpHeaders.CacheControl, value.toString())

/**
 * Appends the `Cache-Control` header with the specified [value] to a response.
 */
public fun HeadersBuilder.cacheControl(value: CacheControl): Unit = set(HttpHeaders.CacheControl, value.toString())

/**
 * Appends the `Content-Range` header with the specified [range] and [fullLength] to a response.
 */
public fun HeadersBuilder.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}

/**
 * Appends the `Content-Range` header with the specified [range] and [fullLength] to a response.
 */
public fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: RangeUnits
) {
    contentRange(range, fullLength, unit.unitToken)
}

/**
 * Appends the `Content-Range` header with the specified [range] and [fullLength] to a response.
 */
public fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}
