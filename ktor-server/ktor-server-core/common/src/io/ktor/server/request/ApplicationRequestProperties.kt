/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.utils.io.charsets.*

/**
 * Gets the first value of a [name] header or returns `null` if missing.
 */
public fun ApplicationRequest.header(name: String): String? = headers[name]

/**
 * Gets a request's query string or returns an empty string if missing.
 */
public fun ApplicationRequest.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Gets a request's content type or returns `ContentType.Any`.
 */
public fun ApplicationRequest.contentType(): ContentType =
    header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Gets a request's `Content-Length` header value.
 */
public fun ApplicationRequest.contentLength(): Long? =
    header(HttpHeaders.ContentLength)?.toLongOrNull()

/**
 * Gets a request's charset.
 */
public fun ApplicationRequest.contentCharset(): Charset? = contentType().charset()

/**
 * A document name is a substring after the last slash but before a query string.
 */
public fun ApplicationRequest.document(): String = path().substringAfterLast('/')

/**
 * Get a request's URL path without a query string.
 */
public fun ApplicationRequest.path(): String = origin.uri.substringBefore('?')

/**
 * Get a request's `Authorization` header value.
 */
public fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Get a request's `Location` header value.
 */
public fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)

/**
 * Get a request's `Accept` header value.
 */
public fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)

/**
 * Gets the `Accept` header content types sorted according to their qualities.
 */
public fun ApplicationRequest.acceptItems(): List<HeaderValue> =
    parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Gets a request's `Accept-Encoding` header value.
 */
public fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Gets the `Accept-Encoding` header encoding types sorted according to their qualities.
 */
public fun ApplicationRequest.acceptEncodingItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Gets a request's `Accept-Language` header value.
 */
public fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Gets the `Accept-Language` header languages sorted according to their qualities.
 */
public fun ApplicationRequest.acceptLanguageItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Gets a request's `Accept-Charset` header value.
 */
public fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)

/**
 * Gets the `Accept-Charset` header charsets sorted according to their qualities.
 */
public fun ApplicationRequest.acceptCharsetItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Checks whether a request's body is chunk-encoded.
 */
public fun ApplicationRequest.isChunked(): Boolean =
    header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Checks whether a request body is multipart-encoded.
 */
public fun ApplicationRequest.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)

/**
 * Gets a request's `User-Agent` header value.
 */
public fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Gets a request's `Cache-Control` header value.
 */
public fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Gets a request's host value without a port.
 * @see [port]
 */
public fun ApplicationRequest.host(): String = origin.serverHost

/**
 * Gets a request's port extracted from the `Host` header value.
 * @see [host]
 */
public fun ApplicationRequest.port(): Int = origin.serverPort

/**
 * Gets ranges parsed from a request's `Range` header value.
 */
public fun ApplicationRequest.ranges(): RangesSpecifier? =
    header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Gets a request's URI, including a query string.
 */
public val ApplicationRequest.uri: String get() = origin.uri

/**
 * Gets a request HTTP method possibly overridden using the `X-Http-Method-Override` header.
 */
public val ApplicationRequest.httpMethod: HttpMethod get() = origin.method

/**
 * Gets a request's HTTP version.
 */
public val ApplicationRequest.httpVersion: String get() = origin.version
