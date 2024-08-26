import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class WriterReaderTest {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testWriterOnCancelled() = testSuspend {
        val job = Job()
        job.cancel()

        val writer = GlobalScope.writer(coroutineContext = job) {
        }

        assertFailsWith<CancellationException> {
            writer.channel.readByte()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReaderOnCancelled() = testSuspend {
        val job = Job()
        job.cancel()

        val reader = GlobalScope.reader(coroutineContext = job) {
        }

        delay(100L)

        assertFailsWith<CancellationException> {
            reader.channel.writeByte(42)
        }
    }

    @Test
    fun testReaderWaitsNestedLaunch() = testSuspend {
        val incoming = ByteChannel()
        val out = reader {
            launch {
                channel.copyAndClose(incoming)
            }
        }.channel

        delay(1000)
        out.writeByte(42)
        out.flushAndClose()

        assertEquals(42, incoming.readByte())
    }

    @Test
    fun testWriterWaitsNestedLaunch() = testSuspend {
        val out = ByteChannel()

        val incoming = writer {
            launch {
                out.copyAndClose(channel)
            }
        }.channel

        delay(1000)
        out.writeByte(42)
        out.flushAndClose()

        assertEquals(42, incoming.readByte())
    }
}
