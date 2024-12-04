/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer

/**
 * Read the specified number of bytes specified (optional, read all remaining by default)
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.readBytes)
 */
public fun Buffer.readBytes(count: Int = size.toInt()): ByteArray {
    return readByteArray(count)
}

internal fun Buffer.isEmpty(): Boolean = size == 0L

public class BufferLimitExceededException(message: String) : Exception(message)
