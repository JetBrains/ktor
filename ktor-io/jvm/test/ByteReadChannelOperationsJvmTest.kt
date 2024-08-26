import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteReadChannelOperationsJvmTest {

    @Test
    fun testReadAvailableBlockFromEmpty() {
        val channel = ByteChannel()
        assertEquals(-1, channel.readAvailable { _ -> 0 })
    }

    @Test
    fun testReadAvailableBlockFromClosed() {
        val channel = ByteChannel()
        channel.close()
        assertEquals(-1, channel.readAvailable { _ -> 0 })
    }

    @Test
    fun testReadAvailableBlockAfterRead() = runBlocking {
        val channel = ByteChannel()
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })
        channel.writeFully(byteArrayOf(1, 2, 3))
        channel.flush()
        assertEquals(3, channel.readAvailable { buffer -> buffer.remaining() })
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })

        channel.close()
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })
    }
}
