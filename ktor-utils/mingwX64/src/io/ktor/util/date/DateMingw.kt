/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionName")
internal actual fun system_time(tm: CValuesRef<tm>?): Long {
    return _mkgmtime(tm).convert()
}

/**
 * Gets current system time in milliseconds since a certain moment in the past,
 * only delta between two subsequent calls makes sense.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun getTimeMillis(): Long = memScoped {
    val timeHolder = alloc<FILETIME>()
    GetSystemTimeAsFileTime(timeHolder.ptr)
    val time = alloc<ULARGE_INTEGER>()
    time.HighPart = timeHolder.dwHighDateTime
    time.LowPart = timeHolder.dwLowDateTime
    (time.QuadPart / 10000U).toLong()
}
