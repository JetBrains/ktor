package io.ktor.utils.io.core

import kotlinx.io.*

/**
 * Copy all bytes to the [output].
 * Depending on actual input and output implementation it could be zero-copy or copy byte per byte.
 * All regular types such as [ByteReadPacket], [BytePacketBuilder], [Input] and [Output]
 * are always optimized so no bytes will be copied.
 */
@Deprecated(
    "Use transferTo instead",
    ReplaceWith("output.transferTo(this)", "kotlinx.io.transferTo"),
    level = DeprecationLevel.ERROR
)
public fun Source.copyTo(output: Sink): Long = transferTo(output)
