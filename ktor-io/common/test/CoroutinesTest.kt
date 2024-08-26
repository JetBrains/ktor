import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class CoroutinesTest {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testWriterWithExtraFlush() = testSuspend {
        val channel = GlobalScope.writer {
            channel.writeByte(42)
            delay(100)
        }.channel

        assertEquals(42, channel.readByte())
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReader() = testSuspend {
        val channel = GlobalScope.reader {
            assertEquals(42, channel.readByte())
        }.channel

        channel.writeByte(42)
        channel.flushAndClose()
    }
}
