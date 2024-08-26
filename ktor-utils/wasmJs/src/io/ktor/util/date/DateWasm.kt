/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlin.js.*

/**
 * Create new gmt date from the [timestamp].
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
public actual fun GMTDate(timestamp: Long?): GMTDate {
    val date = timestamp?.toDouble()?.let { Date(it) } ?: Date()

    if (date.getTime().isNaN()) throw InvalidTimestampException(timestamp!!)

    with(date) {
        /* from SUNDAY 0 -> MONDAY 0 */
        val dayOfWeek = WeekDay.from((getUTCDay() + 6) % 7)

        val month = Month.from(getUTCMonth())

        return GMTDate(
            getUTCSeconds(),
            getUTCMinutes(),
            getUTCHours(),

            dayOfWeek,
            getUTCDate(),
            getUTCFullYear(),

            month,
            getUTCFullYear(),

            getTime().toLong()
        )
    }
}

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
public actual fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate {
    val timestamp = Date.UTC(year, month.ordinal, dayOfMonth, hours, minutes, seconds).toLong()
    return GMTDate(timestamp)
}

/**
 * Invalid exception: possible overflow or underflow
 */
public class InvalidTimestampException(timestamp: Long) : IllegalStateException(
    "Invalid date timestamp exception: $timestamp"
)

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 */
public actual fun getTimeMillis(): Long = Date().getTime().toLong()
