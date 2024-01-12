/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
internal actual fun secureRandom(bytes: ByteArray) {
    bytes.toUByteArray().usePinned { pinned ->
        val result = BCryptGenRandom(
            null,
            pinned.addressOf(0),
            bytes.size.convert(),
            BCRYPT_USE_SYSTEM_PREFERRED_RNG.convert()
        )
        if (result != 0) {
            error("Can't generate random values using BCryptGenRandom: $result")
        }
        bytes.copyUByteArray(pinned.get())
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun ByteArray.copyUByteArray(bytes: UByteArray) {
    for (i in indices) {
        set(i, bytes[i].toByte())
    }
}
