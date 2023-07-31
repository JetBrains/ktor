package io.ktor.utils.io.nio

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import java.nio.channels.*

@Suppress("DEPRECATION")
private class ChannelAsOutput(
    pool: ObjectPool<ChunkBuffer>,
    val channel: WritableByteChannel
) : Output(pool) {
    override fun flush(source: Memory, offset: Int, length: Int) {
        val slice = source.buffer.sliceSafe(offset, length)
        while (slice.hasRemaining()) {
            channel.write(slice)
        }
    }

    override fun closeDestination() {
        channel.close()
    }
}

@Suppress("DEPRECATION")
public fun WritableByteChannel.asOutput(
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): Output = ChannelAsOutput(pool, this)
