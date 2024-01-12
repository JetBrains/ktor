@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package io.ktor.utils.io.core

import kotlinx.cinterop.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
public fun Output.writeFully(src: CPointer<ByteVar>, offset: Int, length: Int) {
    writeFully(src, offset.toLong(), length.toLong())
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
public fun Output.writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
    require(length >= 0L)
    require(offset >= 0L)

    var position = offset
    var rem = length

    writeWhile { chunk ->
        val size = minOf(chunk.writeRemaining.toLong(), rem).toInt()
        chunk.writeFully(src, position, size)
        position += size
        rem -= size
        rem > 0
    }
}
