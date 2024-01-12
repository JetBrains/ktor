package io.ktor.utils.io.core

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
internal actual fun Buffer.discardUntilDelimiterImpl(delimiter: Byte): Int {
    val content = content
    var idx = readPosition
    val end = writePosition

    while (idx < end) {
        if (content[idx] == delimiter) break
        idx++
    }

    val start = readPosition
    discardUntilIndex(idx)
    return idx - start
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
internal actual fun Buffer.discardUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte): Int {
    val content = content
    var idx = readPosition
    val end = writePosition

    while (idx < end) {
        val v = content[idx]
        if (v == delimiter1 || v == delimiter2) break
        idx++
    }

    val start = readPosition
    discardUntilIndex(idx)
    return idx - start
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimiterImpl(
    delimiter: Byte,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    check(offset >= 0)
    check(length >= 0)
    check(offset + length <= dst.size)

    return readUntilImpl({ it == delimiter }, dst, offset, length)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimitersImpl(
    delimiter1: Byte,
    delimiter2: Byte,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    check(offset >= 0)
    check(length >= 0)
    check(offset + length <= dst.size)
    check(delimiter1 != delimiter2)

    return readUntilImpl({ it == delimiter1 || it == delimiter2 }, dst, offset, length)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimiterImpl(delimiter: Byte, dst: Output): Int {
    return readUntilImpl({ it == delimiter }, dst)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte, dst: Output): Int {
    check(delimiter1 != delimiter2)

    return readUntilImpl({ it == delimiter1 || it == delimiter2 }, dst)
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    if (length == 0) return 0

    val content = content
    val start = readPosition
    var i = start
    val end = i + minOf(length, readRemaining)

    while (i < end) {
        if (predicate(content[i])) break
        i++
    }

    val copied = i - start
    dst.usePinned { pinned ->
        val dstPointer = pinned.addressOf(offset)
        val srcPointer = (content + start)!!
        memcpy(dstPointer, srcPointer, copied.convert<size_t>())
    }

    discardUntilIndex(i)

    return copied
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: Output
): Int {
    val content = content
    var i = readPosition
    var copiedTotal = 0

    dst.writeWhile { chunk ->
        val start = i
        val end = minOf(i + chunk.writeRemaining, writePosition)

        while (i < end) {
            if (predicate(content[i])) break
            i++
        }

        val size = i - start

        val dstPointer = (chunk.content + chunk.writePosition)!!
        val srcPointer = (content + start)!!
        memcpy(dstPointer, srcPointer, size.convert<size_t>())
        chunk.commitWritten(size)
        copiedTotal += size

        chunk.writeRemaining == 0 && i < end
    }

    discardUntilIndex(i)
    return copiedTotal
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
internal inline val Buffer.content: CPointer<ByteVar> get() = memory.pointer
