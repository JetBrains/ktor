/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.server.http.content

import io.ktor.http.content.*
import io.ktor.server.util.*
import io.ktor.util.date.*
import java.nio.file.attribute.*
import java.time.*

/**
 * Construct [LastModifiedVersion] version from a [ZonedDateTime] instance
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.http.content.LastModifiedVersion)
 */
public fun LastModifiedVersion(lastModified: ZonedDateTime): LastModifiedVersion =
    LastModifiedVersion(lastModified.toGMTDate())

/**
 * Construct [LastModifiedVersion] version from a [FileTime] instance
 */
public fun LastModifiedVersion(lastModified: FileTime): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified.toMillis()))

/**
 * Construct [LastModifiedVersion] version from a [Long] instance
 */
public fun LastModifiedVersion(lastModified: Long): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified))
