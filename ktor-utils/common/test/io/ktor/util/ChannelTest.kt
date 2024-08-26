/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

class ChannelTest {

    @Test
    fun testCopyToFlushesDestination() = testSuspend {
        val source = ByteChannel()
        val destination = ByteChannel()

        launch(Dispatchers.Unconfined) {
            source.copyTo(destination)
        }

        launch(Dispatchers.Unconfined) {
            source.writeByte(1)
            source.flush()
        }

        val byte = destination.readByte()
        assertEquals(1, byte)
        source.close()
    }

    @Test
    fun testCopyToBoth() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        launch(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.close()
        }

        val firstResult = async(Dispatchers.Unconfined) {
            first.readRemaining().readByteArray()
        }
        val secondResult = async(Dispatchers.Unconfined) {
            second.readRemaining().readByteArray()
        }

        val results = listOf(firstResult, secondResult).awaitAll()
        assertArrayEquals(data, results[0])
        assertArrayEquals(data, results[1])
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testCopyToBothCancelSource() = testSuspend {
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        val message = "Expected reason"

        launch(Dispatchers.Default) {
            source.cancel(IllegalStateException(message))
        }

        assertFailsWith<IOException> {
            val firstResult = GlobalScope.async(Dispatchers.Default) {
                first.readRemaining().readByteArray()
            }
            firstResult.await()
        }

        assertFailsWith<IOException> {
            val secondResult = GlobalScope.async(Dispatchers.Default) {
                second.readRemaining().readByteArray()
            }
            secondResult.await()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testCopyToBothCancelFirstReader() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        val message = "Expected reason"

        val sourceResult = GlobalScope.async(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.writeFully(data)
            source.close()
        }

        first.cancel(IllegalStateException(message))

        assertFailsWith<IOException> {
            sourceResult.await()
        }

        assertFailsWith<IOException> {
            val secondResult = GlobalScope.async(Dispatchers.Unconfined) {
                second.readRemaining().readByteArray()
            }
            secondResult.await()
        }
    }

    @OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
    @Test
    fun testCopyToBothCancelSecondReader() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(second, first)

        val message = "Expected reason"

        val sourceResult = GlobalScope.async(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.writeFully(data)
            source.close()
        }

        first.cancel(IllegalStateException(message))

        assertFailsWith<IOException> {
            val secondResult = GlobalScope.async(Dispatchers.Unconfined) {
                second.readRemaining().readByteArray()
            }
            secondResult.await()
        }

        assertFailsWith<IOException>(message) {
            sourceResult.await()
        }
    }
}

private inline fun assertFailsWithMessage(message: String, block: () -> Unit) {
    var fail = false
    try {
        block()
    } catch (cause: Throwable) {
        assertEquals(message, cause.message)
        fail = true
    }

    assertTrue(fail)
}

private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
    assertTrue { expected.contentEquals(actual) }
}
