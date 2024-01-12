/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalUnsignedTypes::class, UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun secureRandom(bytes: ByteArray) {
    val fd = fopen("/dev/urandom", "rb") ?: return
    val size = bytes.size
    bytes.usePinned { pinned ->
        fread(pinned.addressOf(0), 1.convert(), size.convert(), fd)
    }
    fclose(fd)
}
