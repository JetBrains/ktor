/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.server.util.*
import io.ktor.util.date.*
import io.ktor.util.date.Month
import java.text.*
import java.time.*
import java.util.*
import kotlin.test.*

class DateJvmTest {
    @Test
    fun testJvmDate() {
        val dateRaw = GMTDate(1346524199000)
        val date = dateRaw.toJvmDate()

        assertEquals(dateRaw.timestamp, date.time)
    }

    @Test
    fun testJvmDateWithOffSetDateTime() {
        val dateRaw = GMTDate(20, 20, 20, 20, Month.FEBRUARY, 20)
        val date: OffsetDateTime = OffsetDateTime.of(
            dateRaw.year,
            dateRaw.month.ordinal + 1,
            dateRaw.dayOfMonth - 2,
            dateRaw.hours,
            dateRaw.minutes,
            dateRaw.seconds,
            0,
            ZoneOffset.UTC
        )

        assertEquals(dateRaw.toJvmDate(), date.toInstant().toGMTDate().toJvmDate())
    }

    @Test
    fun testJvmDateWithZoneOffset() {
        val gmtDate: GMTDate = GMTDate(0, 0, 12, 1, Month.JANUARY, 2019)
        val convertedDate = gmtDate.toJvmDate().toInstant().atZone(ZoneOffset.systemDefault())
        assertEquals(convertedDate.toGMTDate(), gmtDate)
    }

    @Test
    fun testJvmDateWithSimpleDateFormat() {
        val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        dateFormat.timeZone = TimeZone.getTimeZone(ZoneOffset.systemDefault())
        val date = dateFormat.parse("2019-01-01T12:00:00").toInstant()

        val gmtDate: GMTDate = GMTDate(0, 0, 12, 1, Month.JANUARY, 2019)
        val format: Long = dateFormat.timeZone.getOffset(gmtDate.timestamp).toLong()
        val convertedDate = gmtDate.toJvmDate().toInstant().minusMillis(format)

        assertEquals(date, convertedDate)
    }
}
