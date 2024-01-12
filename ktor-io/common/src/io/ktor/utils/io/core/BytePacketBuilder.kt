@file:Suppress("RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * A builder that provides ability to build byte packets with no knowledge of it's size.
 * Unlike Java's ByteArrayOutputStream it doesn't copy the whole content every time it's internal buffer overflows
 * but chunks buffers instead. Packet building via [build] function is O(1) operation and only does instantiate
 * a new [ByteReadPacket]. Once a byte packet has been built via [build] function call, the builder could be
 * reused again. You also can discard all written bytes via [reset] or [release]. Please note that an instance of
 * builder need to be terminated either via [build] function invocation or via [release] call otherwise it will
 * cause byte buffer leak so that may have performance impact.
 *
 * Byte packet builder is also an [Appendable] so it does append UTF-8 characters to a packet
 *
 * ```
 * buildPacket {
 *     listOf(1,2,3).joinTo(this, separator = ",")
 * }
 * ```
 */
@Suppress("DEPRECATION")
public class BytePacketBuilder(
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : Output(pool) {

    /**
     * Number of bytes written to the builder after the creation or the last reset.
     */
    public val size: Int
        get() = _size

    /**
     * If no bytes were written or the builder has been reset.
     */
    public val isEmpty: Boolean
        get() = _size == 0

    /**
     * If at least one byte was written after the creation or the last reset.
     */
    public val isNotEmpty: Boolean
        get() = _size > 0

    @PublishedApi
    internal val _pool: ObjectPool<ChunkBuffer>
        get() = pool

    /**
     * Does nothing for memory-backed output
     */
    final override fun closeDestination() {
    }

    /**
     * Does nothing for memory-backed output
     */
    final override fun flush(source: Memory, offset: Int, length: Int) {
    }

    override fun append(value: Char): BytePacketBuilder {
        return super.append(value) as BytePacketBuilder
    }

    override fun append(value: CharSequence?): BytePacketBuilder {
        return super.append(value) as BytePacketBuilder
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): BytePacketBuilder {
        return super.append(value, startIndex, endIndex) as BytePacketBuilder
    }

    /**
     * Builds byte packet instance and resets builder's state to be able to build another one packet if needed
     */
    public fun build(): ByteReadPacket {
        val size = size

        return when (val head = stealAll()) {
            null -> ByteReadPacket.Empty
            else -> ByteReadPacket(head, size.toLong(), pool)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "BytePacketBuilder[0x${hashCode().toHexString()}]"
    }
}
