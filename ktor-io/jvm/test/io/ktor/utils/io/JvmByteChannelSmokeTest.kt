package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.coroutines.debug.junit5.*
import java.nio.*
import kotlin.test.*

@CoroutinesTimeout(10_000, cancelOnTimeout = true)
open class JvmByteChannelSmokeTest : ByteChannelSmokeTest() {

    @Suppress("DEPRECATION")
    @Test
    fun testReadAndWriteFullyByteBuffer() {
        runTest {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val dst = ByteArray(5)

            val ch: ByteChannel = ch

            ch.writeFully(ByteBuffer.wrap(bytes))
            ch.flush()
            assertEquals(5, ch.availableForRead)
            ch.readFully(ByteBuffer.wrap(dst))
            assertTrue { dst.contentEquals(bytes) }

            ch.writeFully(ByteBuffer.wrap(bytes))
            ch.flush()

            val dst2 = ByteArray(4)
            ch.readFully(ByteBuffer.wrap(dst2))

            assertEquals(1, ch.availableForRead)
            assertEquals(5, ch.readByte())

            ch.close()

            try {
                ch.readFully(ByteBuffer.wrap(dst))
                fail("")
            } catch (expected: EOFException) {
            }
        }
    }

    @Test
    fun testReadAndWritePartiallyByteBuffer() {
        runTest {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)

            assertEquals(5, ch.writeAvailable(ByteBuffer.wrap(bytes)))
            ch.flush()
            assertEquals(5, ch.readAvailable(ByteBuffer.allocate(100)))

            repeat(Size / bytes.size) {
                assertNotEquals(0, ch.writeAvailable(ByteBuffer.wrap(bytes)))
                ch.flush()
            }

            ch.readAvailable(ByteArray(ch.availableForRead - 1))
            assertEquals(1, ch.readAvailable(ByteBuffer.allocate(100)))

            ch.close()
        }
    }

    @Suppress("DEPRECATION")
    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(ChunkBuffer.Empty, autoFlush)
    }
}
