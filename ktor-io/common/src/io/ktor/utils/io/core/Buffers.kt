package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer

/**
 * Read the specified number of bytes specified (optional, read all remaining by default)
 */
public fun Buffer.readBytes(count: Int = size.toInt()): ByteArray {
    return readByteArray(count)
}

internal fun Buffer.isEmpty(): Boolean = size == 0L

public class BufferLimitExceededException(message: String) : Exception(message)
