/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.nio.*
import java.util.zip.*
import kotlin.coroutines.*

private const val GZIP_HEADER_SIZE: Int = 10

// GZIP header flags bits
private object GzipHeaderFlags {

    // Is ASCII
    const val FTEXT = 1 shl 0

    // Has header CRC16
    const val FHCRC = 1 shl 1

    // Extra fields present
    const val EXTRA = 1 shl 2

    // File name present
    const val FNAME = 1 shl 3

    // File comment present
    const val FCOMMENT = 1 shl 4
}

private infix fun Int.has(flag: Int) = this and flag != 0

/**
 * Implementation of Deflate [Encoder].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.Deflate)
 */
public val Deflate: Encoder = object : Encoder {
    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        source.deflated(gzip = false, coroutineContext = coroutineContext)

    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        source.deflated(gzip = false, coroutineContext = coroutineContext)

    override fun decode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        inflate(source, gzip = false, coroutineContext = coroutineContext)
}

/**
 * Implementation of GZip [Encoder].
 */
public val GZip: Encoder = object : Encoder {
    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        source.deflated(gzip = true, coroutineContext = coroutineContext)

    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        source.deflated(gzip = true, coroutineContext = coroutineContext)

    override fun decode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        inflate(source, coroutineContext = coroutineContext)
}

@OptIn(DelicateCoroutinesApi::class)
private fun inflate(
    source: ByteReadChannel,
    gzip: Boolean = true,
    coroutineContext: CoroutineContext
): ByteReadChannel = GlobalScope.writer(coroutineContext) {
    val readBuffer = KtorDefaultPool.borrow()
    val writeBuffer = KtorDefaultPool.borrow()
    val inflater = Inflater(true)
    val checksum = CRC32()

    if (gzip) {
        val header = source.readPacket(GZIP_HEADER_SIZE)
        val magic = header.readShortLittleEndian()
        val format = header.readByte()
        val flags = header.readByte().toInt()

        // Next parts of the header are not used for now,
        // uncomment the following lines once you need them

        // val time = header.readInt()
        // val extraFlags = header.readByte()
        // val osType = header.readByte()

        // however we have to discard them to prevent a memory leak
        header.discard()

        // skip the extra header if present
        if (flags and GzipHeaderFlags.EXTRA != 0) {
            val extraLen = source.readShort().toLong()
            source.discardExact(extraLen)
        }

        check(magic == GZIP_MAGIC) { "GZIP magic invalid: $magic" }
        check(format.toInt() == Deflater.DEFLATED) { "Deflater method unsupported: $format." }
        check(!(flags has GzipHeaderFlags.FNAME)) { "Gzip file name not supported" }
        check(!(flags has GzipHeaderFlags.FCOMMENT)) { "Gzip file comment not supported" }

        // skip the header CRC if present
        if (flags has GzipHeaderFlags.FHCRC) {
            source.discardExact(2)
        }
    }

    try {
        var totalSize = 0
        while (!source.isClosedForRead) {
            if (source.readAvailable(readBuffer) <= 0) continue
            readBuffer.flip()

            inflater.setInput(readBuffer.array(), readBuffer.position(), readBuffer.remaining())

            while (!inflater.needsInput() && !inflater.finished()) {
                totalSize += inflater.inflateTo(channel, writeBuffer, checksum)
                readBuffer.position(readBuffer.limit() - inflater.remaining)
            }

            readBuffer.compact()
        }

        source.closedCause?.let { throw it }

        readBuffer.flip()

        while (!inflater.finished()) {
            totalSize += inflater.inflateTo(channel, writeBuffer, checksum)
            readBuffer.position(readBuffer.limit() - inflater.remaining)
        }

        if (gzip) {
            check(readBuffer.remaining() == 8) {
                "Expected 8 bytes in the trailer. Actual: ${readBuffer.remaining()} $"
            }

            readBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val expectedChecksum = readBuffer.getInt(readBuffer.position())
            val expectedSize = readBuffer.getInt(readBuffer.position() + 4)

            check(checksum.value.toInt() == expectedChecksum) { "Gzip checksum invalid." }
            check(totalSize == expectedSize) { "Gzip size invalid. Expected $expectedSize, actual $totalSize" }
        } else {
            check(!readBuffer.hasRemaining())
        }
    } catch (cause: Throwable) {
        throw cause
    } finally {
        inflater.end()
        KtorDefaultPool.recycle(readBuffer)
        KtorDefaultPool.recycle(writeBuffer)
    }
}.channel

private suspend fun Inflater.inflateTo(channel: ByteWriteChannel, buffer: ByteBuffer, checksum: Checksum): Int {
    buffer.clear()

    val inflated = inflate(buffer.array(), buffer.position(), buffer.remaining())
    buffer.position(buffer.position() + inflated)
    buffer.flip()

    checksum.updateKeepPosition(buffer)

    channel.writeFully(buffer)
    return inflated
}
