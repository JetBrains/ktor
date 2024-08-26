/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Format `Content-Range` header value
 */
public fun contentRangeHeaderValue(
    range: LongRange?,
    fullLength: Long? = null,
    unit: RangeUnits = RangeUnits.Bytes
): String =
    contentRangeHeaderValue(range, fullLength, unit.unitToken)

/**
 * Format `Content-Range` header value
 */
public fun contentRangeHeaderValue(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
): String = buildString {
    append(unit)
    append(" ")
    if (range != null) {
        append(range.first)
        append('-')
        append(range.last)
    } else {
        append('*')
    }
    append('/')
    append(fullLength ?: "*")
}
