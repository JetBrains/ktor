import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteChannelTest {

    @Test
    fun testReadFromEmpty() = testSuspend {
        val channel = ByteChannel()
        channel.flushAndClose()

        assertFailsWith<EOFException> {
            channel.readByte()
        }
    }

    @Test
    fun testWriteReadByte() = testSuspend {
        val channel = ByteChannel()
        channel.writeByte(42)
        channel.flushAndClose()
        assertEquals(42, channel.readByte())
    }

    @Test
    fun testCancel() = testSuspend {
        val channel = ByteChannel()
        channel.cancel()

        assertFailsWith<IOException> {
            channel.readByte()
        }
    }

    @Test
    fun testWriteInClosedChannel() = testSuspend {
        val channel = ByteChannel()
        channel.flushAndClose()

        assertTrue(channel.isClosedForWrite)
        assertFailsWith<ClosedWriteChannelException> {
            channel.writeByte(42)
        }
    }

    @Test
    fun testCreateFromArray() = testSuspend {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        val channel = ByteReadChannel(array)
        val result = channel.toByteArray()
        assertTrue(array.contentEquals(result))
    }

    @Test
    fun testChannelFromString() = testSuspend {
        val string = "Hello, world!"
        val channel = ByteReadChannel(string)
        val result = channel.readRemaining().readText()
        assertEquals(string, result)
    }

    @Test
    fun testCancelByteReadChannel() = testSuspend {
        val channel = ByteReadChannel(byteArrayOf(1, 2, 3, 4, 5))
        channel.cancel()
        assertFailsWith<IOException> {
            channel.readByte()
        }
    }

    @Test
    fun testCloseAfterAwait() = testSuspend {
        val channel = ByteChannel()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.awaitContent()
        }

        channel.flushAndClose()
        job.join()
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testChannelMaxSize() = testSuspend(timeoutMillis = 1000) {
        val channel = ByteChannel()
        val job = launch(Dispatchers.Unconfined) {
            channel.writeFully(ByteArray(CHANNEL_MAX_SIZE))
        }

        delay(100)
        assertFalse(job.isCompleted)

        channel.readByte()
        job.join()
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testChannelMaxSizeWithException() = testSuspend {
        val channel = ByteChannel()
        var writerThrows = false
        val deferred = async(Dispatchers.Unconfined) {
            try {
                channel.writeFully(ByteArray(CHANNEL_MAX_SIZE))
            } catch (cause: IOException) {
                writerThrows = true
            }
        }

        assertFalse(deferred.isCompleted)

        channel.cancel()
        deferred.await()

        assertTrue(writerThrows)
    }

    @Test
    fun testIsCloseForReadAfterCancel() = testSuspend {
        val packet = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }

        val channel = ByteChannel()
        channel.writePacket(packet)
        channel.flush()
        channel.cancel()

        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun testWriteAndFlushResumeReader() = testSuspend {
        val channel = ByteChannel()
        val reader = async {
            channel.readByte()
        }

        yield()

        channel.writeByte(42)
        channel.flush()

        assertEquals(42, reader.await())
    }
}
