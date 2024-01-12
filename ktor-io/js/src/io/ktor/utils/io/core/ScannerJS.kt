package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import org.khronos.webgl.*

private fun Memory.asInt8Array(): Int8Array {
    return Int8Array(view.buffer, view.byteOffset, view.byteLength)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.discardUntilDelimiterImpl(delimiter: Byte): Int {
    val content = memory.asInt8Array()
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
internal actual fun Buffer.discardUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte): Int {
    val content = memory.asInt8Array()
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
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    val array = memory.asInt8Array()
    val start = readPosition
    var i = start
    val end = i + minOf(length, readRemaining)

    while (i < end) {
        if (predicate(array[i])) break
        i++
    }

    val copied = i - start
    val dstArray = dst.asDynamic() as Int8Array
    dstArray.set(array.subarray(start, end), offset)
    discardUntilIndex(i)

    return copied
}

@Suppress("DEPRECATION")
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: Output
): Int {
    val array = memory.asInt8Array()
    var i = readPosition
    var copiedTotal = 0

    dst.writeWhile { chunk ->
        chunk.writeFully(chunk, 0)
        val start = i
        val end = minOf(i + chunk.writeRemaining, writePosition)

        while (i < end) {
            if (predicate(array[i])) break
            i++
        }

        val size = i - start

        chunk.memory.asInt8Array().set(array.subarray(start, i), chunk.writePosition)
        chunk.commitWritten(size)
        copiedTotal += size

        chunk.writeRemaining == 0 && i < end
    }

    discardUntilIndex(i)
    return copiedTotal
}
