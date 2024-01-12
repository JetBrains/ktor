/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import java.time.*
import java.time.format.*
import java.time.temporal.*
import java.util.*

/**
 * Format as HTTP date (GMT)
 */
public fun Temporal.toHttpDateString(): String = httpDateFormat.format(this)

private val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")

/**
 * Default HTTP date format
 */
public val httpDateFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("EEE, dd MMM yyyy HH:mm:ss z")
    .withLocale(Locale.US)
    .withZone(GreenwichMeanTime)!!
