/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

/**
 * Exposes the [Date API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date) to Kotlin.
 */
internal external class Date() {
    constructor(milliseconds: Double)

    fun getTime(): Double

    fun getUTCDate(): Int

    fun getUTCDay(): Int

    fun getUTCFullYear(): Int

    fun getUTCHours(): Int

    fun getUTCMinutes(): Int

    fun getUTCMonth(): Int

    fun getUTCSeconds(): Int

    companion object {
        fun UTC(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Double
    }
}
