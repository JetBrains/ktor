/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.nio.*
import kotlin.test.*

@OptIn(DelicateCoroutinesApi::class)
class WriterTest {
    @Test
    fun testWriteBigThenClose() = runBlocking {
        val out = ByteChannel()
        val writer = WebSocketWriter(out, coroutineContext)

        val body = ByteBuffer.allocate(65535)
        while (body.hasRemaining()) {
            body.put(0x77)
        }
        body.flip()

        writer.send(Frame.Binary(true, body))
        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))

        val bytesWritten = out.toByteArray().takeLast(4).joinToString {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

        assertEquals(true, writer.outgoing.isClosedForSend)
        assertEquals("88, 02, 03, e8", bytesWritten)
    }

    @Test
    fun testWriteDataAfterClose() = runBlocking {
        val out = ByteChannel()
        val writer = WebSocketWriter(out, coroutineContext)

        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))
        writer.send(Frame.Text("Yo"))

        val bytesWritten = out.toByteArray().takeLast(4).joinToString {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

        writer.flush()

        assertEquals(true, writer.outgoing.isClosedForSend)
        assertEquals("88, 02, 03, e8", bytesWritten)
    }
}
