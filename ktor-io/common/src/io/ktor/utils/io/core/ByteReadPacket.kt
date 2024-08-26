@file:Suppress("RedundantModalityModifier", "FunctionName")

package io.ktor.utils.io.core

import io.ktor.utils.io.pool.*
import kotlinx.io.*

@Deprecated(
    "Use Source instead",
    ReplaceWith("Source", "kotlinx.io.Source")
)
public typealias ByteReadPacket = Source

public val ByteReadPacketEmpty: Source = kotlinx.io.Buffer()

public fun ByteReadPacket(
    array: ByteArray,
    offset: Int = 0,
    length: Int = array.size
): Source = kotlinx.io.Buffer().apply {
    write(array, startIndex = offset, endIndex = offset + length)
}

@OptIn(InternalIoApi::class)
public val Source.remaining: Long
    get() = buffer.size

@Suppress("UNUSED_PARAMETER")
@Deprecated(
    "Use Buffer instead",
    ReplaceWith("Buffer()", "kotlinx.io.Buffer")
)
public fun Sink(pool: ObjectPool<*>): kotlinx.io.Buffer = kotlinx.io.Buffer()

@Deprecated(
    "Use Buffer instead",
    ReplaceWith("Buffer()", "kotlinx.io.Buffer")
)
public fun Sink(): kotlinx.io.Buffer = kotlinx.io.Buffer()

@OptIn(InternalIoApi::class)
public fun Source.readAvailable(out: kotlinx.io.Buffer): Int {
    val result = buffer.size
    out.transferFrom(this)
    return result.toInt()
}

@OptIn(InternalIoApi::class)
public fun Source.copy(): Source = buffer.copy()

@OptIn(InternalIoApi::class)
public fun Source.readShortLittleEndian(): Short {
    return buffer.readShortLe()
}

@OptIn(InternalIoApi::class)
public fun Source.discard(count: Long = Long.MAX_VALUE): Long {
    request(count)
    val countToDiscard = minOf(count, remaining)
    buffer.skip(countToDiscard)
    return countToDiscard
}

@OptIn(InternalIoApi::class)
public fun Source.takeWhile(block: (kotlinx.io.Buffer) -> Boolean) {
    while (!exhausted() && block(buffer)) {
    }
}

public fun Source.readFully(out: ByteArray, offset: Int = 0, length: Int = out.size - offset) {
    readTo(out, offset, offset + length)
}

@OptIn(InternalIoApi::class)
public fun <T> Source.preview(function: (Source) -> T): T {
    return buffer.peek().use(function)
}

@OptIn(InternalIoApi::class)
public fun <T> Sink.preview(function: (Source) -> T): T {
    return buffer.peek().use(function)
}

@Deprecated(
    "Use close instead",
    ReplaceWith("this.close()")
)
public fun Source.release() {
    close()
}
