/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Returns `true` if [other] range is fully contained inside [this] range
 */
public operator fun LongRange.contains(other: LongRange): Boolean =
    other.first >= start && other.last <= endInclusive
