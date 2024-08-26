import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlinx.io.Buffer
import java.nio.*
import kotlin.random.*
import kotlin.test.*
import kotlin.text.String

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteReadPacketExtensionsJvmTest {

    @Test
    fun testCreateByteReadPacket() = runTest {
        val bb = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        val packet = ByteReadPacket(bb)

        assertContentEquals(byteArrayOf(1, 2, 3), packet.readByteArray())
    }

    @Test
    fun testReadAvailable() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(3)
        assertEquals(3, buffer.readAvailable(bb))
        bb.flip()
        val text = String(bb.array(), 0, bb.remaining())
        assertEquals("123", text)
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadAvailableLarge() = runTest {
        val largeData = Random.nextBytes(10000)

        val buffer = Buffer()
        buffer.write(largeData)

        val bb = ByteBuffer.allocate(10000)
        assertEquals(8192, buffer.readAvailable(bb))
        assertEquals(1808, buffer.readAvailable(bb))
        bb.flip()
        val result = bb.array().copyOfRange(0, bb.remaining())
        assertContentEquals(largeData, result)
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadAvailableFromEmpty() = runTest {
        val buffer = Buffer()

        val bb = ByteBuffer.allocate(1)
        assertEquals(0, buffer.readAvailable(bb))
        bb.flip()
        assertEquals(0, bb.remaining())
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadAvailableToEmpty() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(0)
        assertEquals(0, buffer.readAvailable(bb))
        assertEquals(3, buffer.remaining)
    }

    @Test
    fun testReadAvailablePartially() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(1)
        assertEquals(1, buffer.readAvailable(bb))
        assertEquals(2, buffer.remaining)
    }

    @Test
    fun testReadFully() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(3)
        buffer.readFully(bb)
        bb.flip()
        val text = String(bb.array(), 0, bb.remaining())
        assertEquals("123", text)
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadFullyLarge() = runTest {
        val largeData = Random.nextBytes(10000)

        val buffer = Buffer()
        buffer.write(largeData)

        val bb = ByteBuffer.allocate(10000)
        buffer.readFully(bb)
        bb.flip()
        val result = bb.array().copyOfRange(0, bb.remaining())
        assertContentEquals(largeData, result)
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadFullyFromEmpty() = runTest {
        val buffer = Buffer()

        val bb = ByteBuffer.allocate(1)
        buffer.readFully(bb)
        bb.flip()
        assertEquals(0, bb.remaining())
    }

    @Test
    fun testReadFullyToEmpty() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(0)
        buffer.readFully(bb)
        assertEquals(3, buffer.remaining)
    }

    @Test
    fun testReadFullyPartially() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        val bb = ByteBuffer.allocate(1)
        buffer.readFully(bb)
        assertEquals(2, buffer.remaining)
    }

    @Test
    fun testRead() = runTest {
        val buffer = Buffer()
        buffer.writeText("123")

        buffer.read { bb ->
            val result = ByteArray(bb.remaining())
            bb.get(result)
            val text = result.decodeToString()
            assertEquals("123", text)
        }
        assertEquals(0, buffer.remaining)
    }

    @Test
    fun testReadLarge() = runTest {
        val largeData = Random.nextBytes(10000)

        val buffer = Buffer()
        buffer.write(largeData)

        buffer.read { bb ->
            val result = ByteArray(bb.remaining())
            bb.get(result)
            assertEquals(8192, result.size)
            assertContentEquals(largeData.copyOfRange(0, 8192), result)
        }
        buffer.read { bb ->
            val result = ByteArray(bb.remaining())
            bb.get(result)
            assertEquals(1808, result.size)
            assertContentEquals(largeData.copyOfRange(8192, 10000), result)
        }

        assertEquals(0, buffer.remaining)
    }
}
