/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.core.*
import java.io.*

/**
 * Convert io.ktor.utils.io [Input] to java [InputStream]
 */

public fun Input.asStream(): InputStream = object : InputStream() {

    override fun read(): Int {
        if (endOfInput) return -1
        return readByte().toInt()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (this@asStream.endOfInput) return -1
        return readAvailable(buffer, offset, length)
    }

    override fun skip(count: Long): Long = discard(count)

    override fun close() {
        this@asStream.close()
    }
}
