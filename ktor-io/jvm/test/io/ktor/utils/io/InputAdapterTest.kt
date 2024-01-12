/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.internal.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.test.Test

class InputAdapterTest {
    private val ch = ByteChannel(true)

    @AfterTest
    fun dispose() {
        ch.cancel()
    }

    @Test
    fun testClosedReadSingle() {
        ch.close()
        val s = ch.toInputStream()
        assertEquals(-1, s.read())
    }

    @Test
    fun testClosedReadBuffer() {
        ch.close()
        val s = ch.toInputStream()
        assertEquals(-1, s.read(ByteArray(100)))
    }

    @Test
    fun testReadSingleAfterWrite() = runBlocking {
        ch.writeStringUtf8("123")
        val s = ch.toInputStream()
        assertEquals(0x31, s.read())
        assertEquals(0x32, s.read())
        assertEquals(0x33, s.read())
    }

    @Test
    fun testReadSingleAfterWriteWithClose() = runBlocking {
        ch.writeStringUtf8("123")
        ch.close()
        val s = ch.toInputStream()
        assertEquals(0x31, s.read())
        assertEquals(0x32, s.read())
        assertEquals(0x33, s.read())
        assertEquals(-1, s.read())
    }

    @Test
    fun testReadBufferAfterWrite() = runBlocking {
        ch.writeStringUtf8("123")
        val s = ch.toInputStream()
        val array = ByteArray(3)
        assertEquals(3, s.read(array))
        assertEquals("49, 50, 51", array.joinToString(", "))
    }

    @Test
    fun testReadBufferSmallAfterWrite() = runBlocking {
        ch.writeStringUtf8("123")
        val s = ch.toInputStream()
        val array = ByteArray(2)
        assertEquals(2, s.read(array))
        assertEquals("49, 50", array.joinToString(", "))

        assertEquals(1, s.read(array))
        assertEquals(0x33, array[0])
    }

    @Test
    fun testReadBufferSmallAfterWriteWithClose() = runBlocking {
        ch.writeStringUtf8("123")
        ch.close()

        val s = ch.toInputStream()
        val array = ByteArray(2)
        assertEquals(2, s.read(array))
        assertEquals("49, 50", array.joinToString(", "))

        assertEquals(1, s.read(array))
        assertEquals(0x33, array[0])

        assertEquals(-1, s.read(array))
    }

    @Test
    fun testReadWithParking(): Unit = runBlocking {
        launch {
            val bytes = ch.toInputStream().readBytes()
            assertEquals(8, bytes.size)
        }

        yield()

        ch.writeLong(1)
        ch.close()

        assertTrue { true }
    }
}
