/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import java.util.*

private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")

/**
 * Create new gmt date from the [timestamp].
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
@Suppress("FunctionName")
public actual fun GMTDate(timestamp: Long?): GMTDate =
    Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!.toDate(timestamp)

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
@Suppress("FunctionName")
public actual fun GMTDate(
    seconds: Int,
    minutes: Int,
    hours: Int,
    dayOfMonth: Int,
    month: Month,
    year: Int
): GMTDate = (Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!).apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month.ordinal)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
    set(Calendar.HOUR_OF_DAY, hours)
    set(Calendar.MINUTE, minutes)
    set(Calendar.SECOND, seconds)
    set(Calendar.MILLISECOND, 0)
}.toDate(timestamp = null)

public fun Calendar.toDate(timestamp: Long?): GMTDate {
    timestamp?.let { timeInMillis = it }
    val timeZoneOffset = get(Calendar.ZONE_OFFSET) + get(Calendar.DST_OFFSET)

    val seconds = get(Calendar.SECOND)
    val minutes = get(Calendar.MINUTE)
    val hours = get(Calendar.HOUR_OF_DAY)

    /**
     * from (SUN 1) (MON 2) .. (SAT 7) to (SUN 6) (MON 0) .. (SAT 5)
     */
    val numberOfDay = (get(Calendar.DAY_OF_WEEK) + 7 - 2) % 7
    val dayOfWeek = WeekDay.from(numberOfDay)

    val dayOfMonth = get(Calendar.DAY_OF_MONTH)
    val dayOfYear = get(Calendar.DAY_OF_YEAR)

    val month = Month.from(get(Calendar.MONTH))
    val year = get(Calendar.YEAR)

    return GMTDate(
        seconds, minutes, hours,
        dayOfWeek, dayOfMonth, dayOfYear,
        month, year,
        timeInMillis + timeZoneOffset
    )
}

/**
 * Convert to [Date]
 */
public fun GMTDate.toJvmDate(): Date = Date(timestamp)

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 */
public actual fun getTimeMillis(): Long = System.currentTimeMillis()
