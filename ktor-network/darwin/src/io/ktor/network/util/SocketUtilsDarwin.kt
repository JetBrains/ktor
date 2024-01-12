/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.network.util

import kotlinx.cinterop.*
import kotlinx.cinterop.ByteVar
import platform.darwin.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_inet_ntop(
    family: Int,
    src: CValuesRef<*>?,
    dst: CValuesRef<ByteVar>?,
    size: socklen_t
): CPointer<ByteVar>? = inet_ntop(family, src, dst, size)
