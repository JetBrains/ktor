/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.date.*

/**
 * Lexer over a source string, allowing testing, accepting and capture of spans of characters given
 * some predicates.
 *
 * @property source string to iterate over
 */
internal class StringLexer(val source: String) {
    var index = 0

    val hasRemaining: Boolean get() = index < source.length

    /**
     * Check if the current character satisfies the predicate
     *
     * @param predicate character test
     */
    fun test(predicate: (Char) -> Boolean): Boolean =
        index < source.length && predicate(source[index])

    /**
     * Checks if the current character satisfies the predicate, consuming it is so
     *
     * @param predicate character test
     */
    fun accept(predicate: (Char) -> Boolean): Boolean =
        test(predicate).also { if (it) index++ }

    /**
     * Keep accepting characters while they satisfy the predicate
     *
     * @param predicate character test
     * @see [accept]
     */
    fun acceptWhile(predicate: (Char) -> Boolean): Boolean {
        if (!test(predicate)) return false
        while (test(predicate)) index++
        return true
    }

    /**
     * Run the block on this lexer taking note of the starting and ending index. Returning the span of the
     * source which was traversed.
     *
     * @param block scope of traversal to be captured
     * @return The traversed span of the source string
     */
    inline fun capture(block: StringLexer.() -> Unit): String {
        val start = index
        block()
        return source.substring(start, index)
    }
}

/**
 * Delimiter in the rfc grammar
 */
internal fun Char.isDelimiter(): Boolean =
    this == '\u0009' ||
        this in ('\u0020'..'\u002f') ||
        this in ('\u003b'..'\u0040') ||
        this in ('\u005b'..'\u0060') ||
        this in ('\u007b'..'\u007e')

/**
 * non-delimiter in the rfc grammar
 */
internal fun Char.isNonDelimiter(): Boolean =
    this in ('\u0000'..'\u0008') ||
        this in ('\u000a'..'\u001f') ||
        this in ('0'..'9') ||
        this == ':' ||
        this in ('a'..'z') ||
        this in ('A'..'Z') ||
        this in ('\u007f'..'\u00ff')

/**
 * octet in the rfc grammar
 */
internal fun Char.isOctet(): Boolean =
    this in ('\u0000'..'\u00ff')

/**
 * non-digit in the rfc grammar
 */
internal fun Char.isNonDigit(): Boolean =
    this in ('\u0000'..'\u002f') || this in ('\u004a'..'\u00ff')

/**
 * digit in the rfc grammar
 */
internal fun Char.isDigit(): Boolean =
    this in ('0'..'9')

/**
 * Invoke a lambda when this boolean is false
 */
internal inline fun Boolean.otherwise(block: () -> Unit) {
    if (!this) block()
}

/**
 * Attempt to parse the 'time' rule in the rfc grammar
 *
 * @param success callback on successful parsing, called with (hours, minutes, seconds)
 */
internal inline fun String.tryParseTime(success: (Int, Int, Int) -> Unit) {
    val lexer = StringLexer(this)

    val hour = lexer.capture {
        accept { it.isDigit() }.otherwise { return@tryParseTime }
        accept { it.isDigit() }
    }.toInt()

    lexer.accept { it == ':' }.otherwise { return@tryParseTime }

    val minute = lexer.capture {
        accept { it.isDigit() }.otherwise { return@tryParseTime }
        accept { it.isDigit() }
    }.toInt()

    lexer.accept { it == ':' }.otherwise { return@tryParseTime }

    val second = lexer.capture {
        accept { it.isDigit() }.otherwise { return@tryParseTime }
        accept { it.isDigit() }
    }.toInt()

    if (lexer.accept { it.isNonDigit() }) {
        lexer.acceptWhile { it.isOctet() }
    }

    success(hour, minute, second)
}

/**
 * Attempt to parse the 'month' rule in the rfc grammar
 *
 * @param success callback on successful parsing, called with (month))
 */
internal inline fun String.tryParseMonth(success: (Month) -> Unit) {
    if (length < 3) return

    for (month in Month.entries) {
        if (this.startsWith(month.value, ignoreCase = true)) {
            success(month)
            return
        }
    }

    // Note that if this is ever updated to receive a StringLexer instead of a String,
    // we are supposed to consume all octets after the month
}

/**
 * Attempt to parse the 'day-of-month' rule in the rfc grammar
 *
 * @param success callback on successful parsing, called with (day-of-month)
 */
internal inline fun String.tryParseDayOfMonth(success: (Int) -> Unit) {
    val lexer = StringLexer(this)

    val day = lexer.capture {
        accept { it.isDigit() }.otherwise { return@tryParseDayOfMonth }
        accept { it.isDigit() }
    }.toInt()

    if (lexer.accept { it.isNonDigit() }) {
        lexer.acceptWhile { it.isOctet() }
    }

    success(day)
}

/**
 * Attempt to parse the 'year' rule in the rfc grammar
 *
 * @param success callback on successful parsing, called with (year)
 */
internal inline fun String.tryParseYear(success: (Int) -> Unit) {
    val lexer = StringLexer(this)

    val year = lexer.capture {
        repeat(2) { accept { it.isDigit() }.otherwise { return@tryParseYear } }
        repeat(2) { accept { it.isDigit() } }
    }.toInt()

    if (lexer.accept { it.isNonDigit() }) {
        lexer.acceptWhile { it.isOctet() }
    }

    success(year)
}

/**
 * Handle each 'date-token' in the rfc grammar
 */
internal fun CookieDateBuilder.handleToken(token: String) {
    // 1.  If the found-time flag is not set and the token matches
    //     the time production
    if (hours == null || minutes == null || seconds == null) {
        token.tryParseTime { h, m, s ->
            hours = h
            minutes = m
            seconds = s
            return@handleToken
        }
    }

    // 2.  If the found-day-of-month flag is not set and the date-token
    //     matches the day-of-month production
    if (dayOfMonth == null) {
        token.tryParseDayOfMonth { day ->
            dayOfMonth = day
            return@handleToken
        }
    }

    // 3.  If the found-month flag is not set and the date-token matches
    //     the month production
    if (month == null) {
        token.tryParseMonth { m ->
            month = m
            return@handleToken
        }
    }

    // 4.  If the found-year flag is not set and the date-token matches
    //     the year production
    if (year == null) {
        token.tryParseYear { y ->
            year = y
            return@handleToken
        }
    }
}

/**
 * Parser for RFC6265 cookie dates using the algorithm described in 5.1.1
 *
 * The grammar is the following:
 *
 * cookie-date     = *delimiter date-token-list *delimiter
 * date-token-list = date-token *( 1*delimiter date-token )
 * date-token      = 1*non-delimiter
 *
 * delimiter       = %x09 / %x20-2F / %x3B-40 / %x5B-60 / %x7B-7E
 * non-delimiter   = %x00-08 / %x0A-1F / DIGIT / ":" / ALPHA / %x7F-FF
 * non-digit       = %x00-2F / %x3A-FF
 *
 * day-of-month    = 1*2DIGIT ( non-digit *OCTET )
 * month           = ( "jan" / "feb" / "mar" / "apr" /
 * "may" / "jun" / "jul" / "aug" /
 * "sep" / "oct" / "nov" / "dec" ) *OCTET
 * year            = 2*4DIGIT ( non-digit *OCTET )
 * time            = hms-time ( non-digit *OCTET )
 * hms-time        = time-field ":" time-field ":" time-field
 * time-field      = 1*2DIGIT
 *
 *
 */
internal class CookieDateParser {

    private fun <T> checkFieldNotNull(source: String, name: String, field: T?) {
        if (null == field) {
            throw InvalidCookieDateException(source, "Could not find $name")
        }
    }

    private fun checkRequirement(source: String, requirement: Boolean, msg: () -> String) {
        if (!requirement) {
            throw InvalidCookieDateException(source, msg())
        }
    }

    /**
     * Parses cookie expiration date from the [source].
     */
    fun parse(source: String): GMTDate {
        val lexer = StringLexer(source)
        val builder = CookieDateBuilder()

        lexer.acceptWhile { it.isDelimiter() }

        while (lexer.hasRemaining) {
            if (lexer.test { it.isNonDelimiter() }) {
                val token = lexer.capture { acceptWhile { it.isNonDelimiter() } }

                builder.handleToken(token)

                lexer.acceptWhile { it.isDelimiter() }
            }
        }

        /**
         * 3. If the year-value is greater than or equal to 70 and less than or
         * equal to 99, increment the year-value by 1900
         * 4. If the year-value is greater than or equal to 0 and less than or
         * equal to 69, increment the year-value by 2000.
         */
        when (builder.year) {
            in 70..99 -> builder.year = builder.year!! + 1900
            in 0..69 -> builder.year = builder.year!! + 2000
        }

        checkFieldNotNull(source, "day-of-month", builder.dayOfMonth)
        checkFieldNotNull(source, "month", builder.month)
        checkFieldNotNull(source, "year", builder.year)
        checkFieldNotNull(source, "time", builder.hours)
        checkFieldNotNull(source, "time", builder.minutes)
        checkFieldNotNull(source, "time", builder.seconds)

        checkRequirement(source, builder.dayOfMonth in 1..31) { "day-of-month not in [1,31]" }
        checkRequirement(source, builder.year!! >= 1601) { "year >= 1601" }
        checkRequirement(source, builder.hours!! <= 23) { "hours > 23" }
        checkRequirement(source, builder.minutes!! <= 59) { "minutes > 59" }
        checkRequirement(source, builder.seconds!! <= 59) { "seconds > 59" }

        return builder.build()
    }
}

internal class CookieDateBuilder {
    var seconds: Int? = null
    var minutes: Int? = null
    var hours: Int? = null

    var dayOfMonth: Int? = null
    var month: Month? = null
    var year: Int? = null

    fun build(): GMTDate = GMTDate(seconds!!, minutes!!, hours!!, dayOfMonth!!, month!!, year!!)
}

/**
 * Thrown when the date string doesn't satisfy the RFC6265 grammar
 */
internal class InvalidCookieDateException(
    data: String,
    reason: String
) : IllegalStateException("Failed to parse date string: \"${data}\". Reason: \"$reason\"")
