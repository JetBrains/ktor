/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlin.test.*

class BytePacketBuilderExtendedTest : BytePacketBuildTest() {
    override val pool = VerifyingChunkBufferPool()

    @OptIn(ExperimentalForeignApi::class)
    private val buffer = nativeHeap.allocArray<ByteVar>(8888)

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun smokeSingleBufferTestExtended() {
        val p = buildPacket {
            writeFully(ByteArray(2))
            writeFully(buffer, 3)

            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.23)
            writeFloat(1.23f)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(40L, p.remaining)

        p.readFully(ByteArray(2))
        p.readFully(buffer, 3)

        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.23, p.readDouble())
        assertEquals(1.23f, p.readFloat())
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun smokeMultiBufferTestExtended() {
        val p = buildPacket {
            writeFully(ByteArray(9999))
            writeFully(buffer, 8888)
            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.23)
            writeFloat(1.23f)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(18922L, p.remaining)

        p.readFully(ByteArray(9999))
        p.readFully(buffer, 8888)
        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.23, p.readDouble())
        assertEquals(1.23f, p.readFloat())
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())
    }

    private inline fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
        val builder = BytePacketBuilder(pool)
        try {
            block(builder)
            return builder.build()
        } catch (t: Throwable) {
            builder.release()
            throw t
        }
    }

    companion object
}
