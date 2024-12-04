/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.nio

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.*

/**
 * Converts a [ReadableByteChannel] to a [ByteReadChannel], enabling asynchronous reading of bytes.
 *
 * @param context the [CoroutineContext] to execute the read operation. Defaults to [Dispatchers.IO].
 * @return a [ByteReadChannel] for reading bytes asynchronously from the given [ReadableByteChannel].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.jvm.nio.toByteReadChannel)
 */
public fun ReadableByteChannel.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Converts a [ReadableByteChannel] into a [RawSource].
 *
 * This extension function wraps the given [ReadableByteChannel] into a [RawSource],
 * enabling efficient reading of bytes from the channel as a source of data.
 *
 * @return a [RawSource] representation of the [ReadableByteChannel].
 */
public fun ReadableByteChannel.asSource(): RawSource =
    ReadableByteChannelSource(this)

/**
 * A data source that reads from a [ReadableByteChannel].
 *
 * This class implements the [RawSource] interface, allowing for the reading
 * of bytes from a [ReadableByteChannel] into a [Buffer].
 *
 * @property channel The [ReadableByteChannel] from which bytes are read.
 */
private open class ReadableByteChannelSource(
    private val channel: ReadableByteChannel,
) : RawSource {
    @OptIn(UnsafeIoApi::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) return 0L

        var readTotal: Int
        val actualByteCount = minOf(byteCount, Int.MAX_VALUE.toLong()).toInt()

        UnsafeBufferOperations.writeToTail(sink, 1) { data, pos, limit ->
            val maxToCopy = minOf(actualByteCount, limit - pos)
            val buffer = ByteBuffer.wrap(data, pos, maxToCopy)
            readTotal = channel.read(buffer)
            maxOf(readTotal, 0)
        }

        return readTotal.toLong()
    }

    override fun close() =
        channel.close()

    override fun toString(): String =
        "ReadableByteChannelSource($channel)"
}
