package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.cinterop.*
import kotlin.test.*

class ChunkBufferNativeTest {
    @Suppress("DEPRECATION")
    private val buffer = ChunkBuffer.Pool.borrow()

    @AfterTest
    fun destroy() {
        @Suppress("DEPRECATION")
        buffer.release(ChunkBuffer.Pool)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReadDirectOnEmpty() {
        var invoked: Boolean
        buffer.readDirect {
            invoked = true
            0
        }.also {
            assertEquals(0, it)
        }
        assertTrue(invoked)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReadDirectNegativeResult() {
        assertFails {
            buffer.readDirect {
                -1
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReadDirectTooManyBytesResult() {
        assertFails {
            buffer.readDirect {
                1
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReadDirect() {
        var result: Int
        buffer.writeByte(7)
        buffer.writeByte(8)
        buffer.readDirect { ptr ->
            result = ptr[0].toInt()
            1
        }.also {
            assertEquals(1, it)
        }
        assertEquals(7, result)
        assertEquals(8, buffer.readByte().toInt())
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReadDirectAtEnd() {
        while (buffer.writeRemaining > 0) {
            buffer.writeByte(1)
        }

        val size = buffer.readRemaining
        buffer.readDirect {
            buffer.readRemaining
        }.also {
            assertEquals(size, it)
        }

        assertEquals(0, buffer.readRemaining)
        buffer.readDirect { 0 }.also {
            assertEquals(0, it)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testWriteDirect() {
        buffer.writeDirect { ptr ->
            ptr[0] = 1.toByte()
            ptr[1] = 2.toByte()
            2
        }.also {
            assertEquals(2, it)
        }

        assertEquals(2, buffer.readRemaining)
        assertEquals(1, buffer.readByte().toInt())
        assertEquals(2, buffer.readByte().toInt())
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testWriteDirectOnFull() {
        val size = buffer.writeRemaining
        buffer.writeDirect { size }
        assertEquals(size, buffer.readRemaining)
        assertEquals(0, buffer.writeRemaining)
        buffer.writeDirect { 0 }
    }
}
