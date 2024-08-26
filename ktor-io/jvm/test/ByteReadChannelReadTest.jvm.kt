import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.nio.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteReadChannelReadTest {

    @Test
    fun testReadAvailableFromEmpty() = runTest {
        val channel = ByteReadChannel(ByteArray(0))

        channel.read(0) { buffer: ByteBuffer ->
            fail()
        }
    }
}
