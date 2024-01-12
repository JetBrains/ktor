/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

open class BytePacketStringTest {
    open val pool: VerifyingChunkBufferPool = VerifyingChunkBufferPool()

    @AfterTest
    fun verifyPool() {
        pool.assertEmpty()
    }

    @Test
    fun testReadLineSingleBuffer() {
        val p = buildPacket {
            append("1\r22\n333\r\n4444")
        }

        assertEquals("1", p.readUTF8Line())
        assertEquals("22", p.readUTF8Line())
        assertEquals("333", p.readUTF8Line())
        assertEquals("4444", p.readUTF8Line())
        assertNull(p.readUTF8Line())
    }

    @Test
    fun testReadLineMultiBuffer() {
        val p = buildPacket {
            repeat(1000) {
                append("1\r22\n333\r\n4444\n")
            }
        }

        repeat(1000) {
            assertEquals("1", p.readUTF8Line())
            assertEquals("22", p.readUTF8Line())
            assertEquals("333", p.readUTF8Line())
            assertEquals("4444", p.readUTF8Line())
        }

        assertNull(p.readUTF8Line())
    }

    @Test
    fun testSingleBufferReadText() {
        val p = buildPacket {
            append("ABC")
        }

        assertEquals("ABC", p.readText())
    }

    @Test
    fun testSingleBufferMultibyteReadText() {
        val p = buildPacket {
            append("ABC\u0422")
        }

        assertEquals("ABC\u0422", p.readText())
    }

    @Test
    fun testMultiBufferReadText() {
        val size = 100000
        val ba = ByteArray(size) {
            'x'.code.toByte()
        }
        val s = CharArray(size) {
            'x'
        }.joinToString("")

        val packet = buildPacket {
            writeFully(ba)
        }

        assertEquals(s, packet.readText())
    }

    @Test
    fun testDecodePacketSingleByte() {
        val packet = buildPacket {
            append("1")
        }

        try {
            assertEquals("1", Charsets.UTF_8.newDecoder().decode(packet))
        } finally {
            packet.release()
        }
    }

    @Test
    fun testDecodePacketMultiByte() {
        val packet = buildPacket {
            append("\u0422")
        }

        try {
            assertEquals("\u0422", Charsets.UTF_8.newDecoder().decode(packet))
        } finally {
            packet.release()
        }
    }

    @Test
    fun testDecodePacketMultiByteSeveralCharacters() {
        val packet = buildPacket {
            append("\u0422e\u0438")
        }

        try {
            assertEquals("\u0422e\u0438", Charsets.UTF_8.newDecoder().decode(packet))
        } finally {
            packet.release()
        }
    }

    @Test
    fun testEncode() {
        assertTrue { byteArrayOf(0x41).contentEquals(Charsets.UTF_8.newEncoder().encode("A").readBytes()) }
        assertTrue {
            byteArrayOf(0x41, 0x42, 0x43).contentEquals(Charsets.UTF_8.newEncoder().encode("ABC").readBytes())
        }
        assertTrue {
            byteArrayOf(0xd0.toByte(), 0xa2.toByte(), 0x41, 0xd0.toByte(), 0xb8.toByte()).contentEquals(
                Charsets.UTF_8.newEncoder().encode("\u0422A\u0438").readBytes()
            )
        }
    }

    @Test
    fun testReadUntilDelimiter() {
        val p = buildPacket {
            append("1,2|3")
        }

        val sb = StringBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("123", sb.toString())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterToPacket() {
        val p = buildPacket {
            append("1,2|3")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("123", sb.build().readText())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterToPacketSingleDelimiterAscii() {
        val p = buildPacket {
            append("1,2,3")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, ",")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("123", sb.build().readText())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterToPacketTwoDelimitersAscii() {
        val p = buildPacket {
            append("1,2|3")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, ",|")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("123", sb.build().readText())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterDifferentLength() {
        val p = buildPacket {
            append("1,23|,4")
        }

        val sb = StringBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("1234", sb.toString())
        assertEquals(listOf(1, 2, 0, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterDifferentLengthToBuilder() {
        val p = buildPacket {
            append("1,23|,4")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("1234", sb.build().readText())
        assertEquals(listOf(1, 2, 0, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterMultibyte() {
        val p = buildPacket {
            append("\u0422,\u0423|\u0424")
        }

        val sb = StringBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("\u0422\u0423\u0424", sb.toString())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterMultibyteToBuilder() {
        val p = buildPacket {
            append("\u0422,\u0423|\u0424")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "|,.")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals("\u0422\u0423\u0424", sb.build().readText())
        assertEquals(listOf(1, 1, 1), counts)
    }

    @Test
    fun testReadUntilDelimiterMultibyteDelimiter() {
        val p = buildPacket {
            append("1\u04222")
        }

        val sb = StringBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "\u0422")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(1)
        }

        assertEquals(listOf(1, 1), counts)
        assertEquals("12", sb.toString())
    }

    @Test
    fun testReadUntilDelimiterMultibyteDelimiterToBuilder() {
        val p = buildPacket {
            append("1\u04222")
        }

        val sb = BytePacketBuilder()
        val counts = mutableListOf<Int>()

        while (true) {
            val rc = p.readUTF8UntilDelimiterTo(sb, "\u0422")
            counts.add(rc)
            if (p.isEmpty) break
            p.discardExact(2)
        }

        assertEquals(listOf(1, 1), counts)
        assertEquals("12", sb.build().readText())
    }

    @Test
    fun testToByteArray() {
        assertEquals(
            byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte()).hexdump(),
            "\uD858\uDE18".toByteArray().hexdump()
        )
    }

    @Test
    fun testEncodeToByteArraySequence() {
        assertEquals(
            byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte()).hexdump(),
            Charsets.UTF_8.newEncoder().encodeToByteArray(
                StringBuilder().apply { append("\uD858\uDE18") }
            ).hexdump()
        )
    }

    @Test
    fun testEncodeToByteArrayCommonImpl() {
        val encoder = Charsets.UTF_8.newEncoder()
        assertEquals(
            byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte()).hexdump(),
            encoder.encodeToByteArrayImpl("\uD858\uDE18").hexdump()
        )
    }

    @Test
    fun testEncodeToByteArrayCommonImplCharSequence() {
        val encoder = Charsets.UTF_8.newEncoder()
        assertEquals(
            byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte()).hexdump(),
            encoder.encodeToByteArrayImpl(StringBuilder().apply { append("\uD858\uDE18") }).hexdump()
        )
    }

    @Test
    fun testToByteArrayLong() {
        val expected = longMultibyteStringBytes().hexdump()
        val actual = longMultibyteString().toString().toByteArray(Charsets.UTF_8).hexdump()

        assertEquals(expected, actual)
    }

    @Test
    fun testEncodeToByteArrayCommonImplCharSequenceLong() {
        val expected = longMultibyteStringBytes().hexdump()
        val actual = Charsets.UTF_8.newEncoder().encodeToByteArray(longMultibyteString()).hexdump()

        assertEquals(expected, actual)
    }

    @Test
    fun stringCtor() {
        val bytes = byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte())
        val actual = String(bytes)

        assertEquals("\uD858\uDE18", actual)
    }

    @Test
    fun stringConstructorFromSlice() {
        val helloString = "Hello, world"
        val helloBytes = helloString.toByteArray()

        assertEquals("Hello", String(helloBytes, 0, 5))
        assertEquals("ello", String(helloBytes, 1, 4))
        assertEquals("ello, ", String(helloBytes, 1, 6))
        assertEquals("world", String(helloBytes, 7, 5))
    }

    @Test
    fun stringCtorEmpty() {
        val actual = String(ByteArray(0))
        assertEquals("", actual)
    }

    @Test
    fun testReadTextExactBytes() {
        var packet = buildPacket {
            append("\u0422e\u0438")
        }

        try {
            assertEquals("\u0422", packet.readTextExactBytes(bytesCount = 2))
        } finally {
            packet.release()
        }

        packet = buildPacket {
            append("\u0422e\u0438")
        }

        try {
            assertEquals("\u0422e", packet.readTextExactBytes(bytesCount = 3))
        } finally {
            packet.release()
        }

        packet = buildPacket {
            append("\u0422e\u0438")
        }

        try {
            assertFails {
                assertEquals("\u0422", packet.readTextExactBytes(bytesCount = 4))
            }
        } finally {
            packet.release()
        }

        packet = buildPacket {
            append("\u0422e\u0438")
        }

        try {
            val text = packet.readTextExactBytes(bytesCount = 5)
            assertEquals(3, text.length)
            assertEquals("\u0422e\u0438", text)
        } finally {
            packet.release()
        }

        val longLine = buildString {
            repeat(8192) {
                append((it and 0xf).toString(16))
            }
        }
        val big = buildPacket {
            append(longLine)
        }

        try {
            for (i in listOf(4095)) { // }, 4089, 4095, 4096, 4097, 4098, 8176, 8192)) {
                val copied = big.copy()

                try {
                    val actual = copied.readTextExactBytes(bytesCount = i)
                    assertEquals(i, actual.length)
                    assertTrue { longLine.substring(0, i) == actual }
                } finally {
                    copied.release()
                }
            }
        } finally {
            big.release()
        }
    }

    @Test
    fun testStringCtorRange() {
        assertEquals("@C", String(byteArrayOf(64, 64, 67, 67), length = 2, offset = 1))
    }

    private fun longMultibyteString() = StringBuilder().apply {
        repeat(10_000) {
            append("\uD858\uDE18")
        }
    }

    private fun longMultibyteStringBytes() = buildPacket {
        val bytes = byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte())
        repeat(10_000) {
            writeFully(bytes)
        }
    }.readBytes()

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

    private fun ByteArray.hexdump() = joinToString(separator = " ") { (it.toInt() and 0xff).toString(16) }
}
