/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.text.*
import java.util.*

private val HTTP_DATE_FORMAT: SimpleDateFormat
    get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

private fun parseHttpDate(date: String): Date = HTTP_DATE_FORMAT.parse(date)

private fun formatHttpDate(date: Date): String = HTTP_DATE_FORMAT.format(date)

/**
 * Set `If-Modified-Since` header.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.ifModifiedSince)
 */
public fun HttpMessageBuilder.ifModifiedSince(date: Date): Unit =
    headers.set(HttpHeaders.IfModifiedSince, formatHttpDate(date))

/**
 * Parse `Last-Modified` header.
 */
public fun HttpMessageBuilder.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }

/**
 * Parse `Expires` header.
 */
public fun HttpMessageBuilder.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }

/**
 * Parse `Last-Modified` header.
 */
public fun HttpMessage.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }

/**
 * Parse `Expires` header.
 */
public fun HttpMessage.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }

/**
 * Parse `Date` header.
 */
public fun HttpMessage.date(): Date? = headers[HttpHeaders.Date]?.let { parseHttpDate(it) }
