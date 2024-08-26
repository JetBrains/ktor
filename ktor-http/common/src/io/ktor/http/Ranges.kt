/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import kotlin.math.*

/**
 * Possible content range units: bytes and none
 * @property unitToken Lower-case unit name
 */
public enum class RangeUnits(public val unitToken: String) {
    /**
     * Range unit `bytes`
     */
    Bytes("bytes"),

    /**
     * Range unit `none`
     */
    None("none")
}

/**
 * Represents a `Range` header's particular range
 */
public sealed class ContentRange {
    /**
     * Represents a `Content-Range` bounded from both sides
     * @property from index from which the content should begin
     * @property to the last index the content should end at (inclusive)
     */
    public data class Bounded(val from: Long, val to: Long) : ContentRange() {
        override fun toString(): String = "$from-$to"
    }

    /**
     * Represents a `Content-Range` bounded at the beginning (skip first bytes, show tail)
     * @property from index from which the content should begin
     */
    public data class TailFrom(val from: Long) : ContentRange() {
        override fun toString(): String = "$from-"
    }

    /**
     * Represents a `Content-Range` bounded by tail size
     * @property lastCount number of tail bytes
     */
    public data class Suffix(val lastCount: Long) : ContentRange() {
        override fun toString(): String = "-$lastCount"
    }
}

/**
 * Parse `Range` header value
 */
public fun parseRangesSpecifier(rangeSpec: String): RangesSpecifier? {
    try {
        val (unit, allRangesString) = rangeSpec.chomp("=") { return null }
        val allRanges = allRangesString.split(',').map {
            if (it.startsWith("-")) {
                ContentRange.Suffix(it.removePrefix("-").toLong())
            } else {
                val (from, to) = it.chomp("-") { "" to "" }
                when {
                    to.isNotEmpty() -> ContentRange.Bounded(from.toLong(), to.toLong())
                    else -> ContentRange.TailFrom(from.toLong())
                }
            }
        }

        if (allRanges.isEmpty() || unit.isEmpty()) {
            return null
        }

        val spec = RangesSpecifier(unit, allRanges)
        return if (spec.isValid()) spec else null
    } catch (e: Throwable) {
        return null // according to the specification we should ignore syntactically incorrect headers
    }
}

internal fun List<ContentRange>.toLongRanges(contentLength: Long) = map {
    when (it) {
        is ContentRange.Bounded -> it.from..it.to.coerceAtMost(contentLength - 1)
        is ContentRange.TailFrom -> it.from until contentLength
        is ContentRange.Suffix -> (contentLength - it.lastCount).coerceAtLeast(0L) until contentLength
    }
}.filterNot { it.isEmpty() }

// O (N^2 + N ln (N) + N)
internal fun List<LongRange>.mergeRangesKeepOrder(): List<LongRange> {
    val sortedMerged = sortedBy { it.first }.fold(ArrayList<LongRange>(size)) { acc, range ->
        when {
            acc.isEmpty() -> acc.add(range)
            acc.last().last < range.first - 1 -> acc.add(range)
            else -> {
                val last = acc.last()
                acc[acc.lastIndex] = last.first..max(last.last, range.last)
            }
        }
        acc
    }
    val result = arrayOfNulls<LongRange>(size)

    for (range in sortedMerged) {
        for (i in indices) {
            if (this[i] in range) {
                result[i] = range
                break
            }
        }
    }

    return result.filterNotNull()
}
