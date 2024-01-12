/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.*

/**
 * Read [Short] in network order(BE) with specified [offset] from [ByteArray].
 */
@InternalAPI
public fun ByteArray.readShort(offset: Int): Short {
    val result = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    return result.toShort()
}
