/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.date

/**
 * Build [GMTDate] parser using [pattern] string.
 *
 * Pattern string format:
 * | Unit     | pattern char | Description                                          |
 * | -------- | ------------ | ---------------------------------------------------- |
 * | Seconds  | s            | parse seconds 0 to 60                                |
 * | Minutes  | m            | parse minutes 0 to 60                                |
 * | Hours    | h            | parse hours 0 to 23                                  |
 * | Month    | M            | parse month from Jan to Dec(see [Month] for details) |
 * | Year     | Y            | parse year                                           |
 * | Any char | *            | Match any character                                  |
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.date.GMTDateParser)
 */
public class GMTDateParser(private val pattern: String) {
    init {
        check(pattern.isNotEmpty()) { "Date parser pattern shouldn't be empty." }
    }

    /**
     * Parse [GMTDate] from [dateString] using [pattern].
     */
    public fun parse(dateString: String): GMTDate {
        val builder = GMTDateBuilder()

        var start = 0
        var current = pattern[start]
        var chunkStart = 0
        var index = 1

        try {
            while (index < pattern.length) {
                if (pattern[index] == current) {
                    index++
                    continue
                }

                val chunkEnd = chunkStart + index - start
                builder.handleToken(current, dateString.substring(chunkStart, chunkEnd))

                chunkStart = chunkEnd
                start = index
                current = pattern[index]

                index++
            }

            if (chunkStart < dateString.length) {
                builder.handleToken(current, dateString.substring(chunkStart))
            }
        } catch (_: Throwable) {
            throw InvalidDateStringException(dateString, chunkStart, pattern)
        }

        return builder.build()
    }

    private fun GMTDateBuilder.handleToken(
        type: Char,
        chunk: String
    ): Unit = when (type) {
        SECONDS -> {
            seconds = chunk.toInt()
        }
        MINUTES -> {
            minutes = chunk.toInt()
        }
        HOURS -> {
            hours = chunk.toInt()
        }
        DAY_OF_MONTH -> {
            dayOfMonth = chunk.toInt()
        }
        MONTH -> {
            month = Month.from(chunk)
        }
        YEAR -> {
            year = chunk.toInt()
        }
        ZONE ->
            check(chunk == "GMT")
        ANY -> Unit
        else -> {
            check(chunk.all { it == type })
        }
    }

    public companion object {
        public const val SECONDS: Char = 's'
        public const val MINUTES: Char = 'm'
        public const val HOURS: Char = 'h'

        public const val DAY_OF_MONTH: Char = 'd'
        public const val MONTH: Char = 'M'
        public const val YEAR: Char = 'Y'

        public const val ZONE: Char = 'z'

        public const val ANY: Char = '*'
    }
}

internal class GMTDateBuilder {
    var seconds: Int? = null
    var minutes: Int? = null
    var hours: Int? = null

    var dayOfMonth: Int? = null
    lateinit var month: Month
    var year: Int? = null

    fun build(): GMTDate = GMTDate(seconds!!, minutes!!, hours!!, dayOfMonth!!, month, year!!)
}

/**
 * Thrown when the date string doesn't the string pattern.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.date.InvalidDateStringException)
 */
public class InvalidDateStringException(
    data: String,
    at: Int,
    pattern: String
) : IllegalStateException("Failed to parse date string: \"${data}\" at index $at. Pattern: \"$pattern\"")
