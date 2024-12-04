/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.server.http.*
import java.time.*
import java.time.temporal.*

/**
 * Append HTTP response header with temporal [date] (date, time and so on)
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.response.header)
 */
public fun ApplicationResponse.header(name: String, date: Temporal): Unit =
    headers.append(name, date.toHttpDateString())

/**
 * Append response `Last-Modified` HTTP header value from [dateTime]
 */
public fun ApplicationResponse.lastModified(dateTime: ZonedDateTime): Unit = header(HttpHeaders.LastModified, dateTime)

/**
 * Append response `Expires` HTTP header [value]
 */
public fun ApplicationResponse.expires(value: LocalDateTime): Unit = header(HttpHeaders.Expires, value)

/**
 * Set 'Last-Modified` header value from [dateTime]
 */
public fun HeadersBuilder.lastModified(dateTime: ZonedDateTime): Unit =
    set(HttpHeaders.LastModified, dateTime.toHttpDateString())

/**
 * Set 'Expires` header value from [expires]
 */
public fun HeadersBuilder.expires(expires: LocalDateTime): Unit = set(HttpHeaders.Expires, expires.toHttpDateString())
