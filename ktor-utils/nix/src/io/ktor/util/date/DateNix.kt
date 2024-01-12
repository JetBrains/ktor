/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("FunctionName")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun system_time(tm: CValuesRef<tm>?): Long = timegm(tm).convert()

/**
 * Gets current system time in milliseconds since a certain moment in the past,
 * only delta between two subsequent calls makes sense.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
public actual fun getTimeMillis(): Long = memScoped {
    val timeHolder = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME.convert(), timeHolder.ptr)
    timeHolder.tv_sec * 1000L + timeHolder.tv_nsec / 1000000L
}
