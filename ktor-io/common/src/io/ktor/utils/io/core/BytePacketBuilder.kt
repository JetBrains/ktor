@file:Suppress("RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import kotlinx.io.*

@Deprecated(
    IO_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("Sink", "kotlinx.io.Sink")
)
public typealias BytePacketBuilder = Sink

@OptIn(InternalIoApi::class)
public val Sink.size: Int get() = buffer.size.toInt()

public fun BytePacketBuilder(): Sink = kotlinx.io.Buffer()

public fun Sink.append(value: CharSequence, startIndex: Int = 0, endIndex: Int = value.length) {
    writeText(value, startIndex, endIndex)
}

@OptIn(InternalIoApi::class)
public fun Sink.build(): Source = buffer

public fun Sink.writeFully(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset) {
    write(buffer, offset, offset + length)
}

public fun Sink.writePacket(packet: Source) {
    transferFrom(packet)
}
