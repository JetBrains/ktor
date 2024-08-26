/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.nio.*
import kotlin.test.*

class WebSocketReaderTest {
    private suspend fun writeFrame(channel: ByteWriteChannel, frame: Frame) {
        val serializer = Serializer()
        serializer.enqueue(frame)

        while (serializer.hasOutstandingBytes) {
            channel.write { buffer: ByteBuffer ->
                serializer.serialize(buffer)
            }
        }
    }

    @Test
    fun testFailFragmentedControl() {
        val channel = ByteChannel(true)
        runBlocking {
            val reader = WebSocketReader(channel, coroutineContext, 1000)
            val frame = Frame.Ping(ByteArray(126))
            writeFrame(channel, frame)
            assertFailsWith<ProtocolViolationException> {
                reader.incoming.receive()
            }
        }
    }

    @Test
    fun testInterruptedContinuation() {
        val channel = ByteChannel(true)
        runBlocking {
            val reader = WebSocketReader(channel, coroutineContext, 1000)
            writeFrame(channel, Frame.Text(false, "Foo".toByteArray()))
            writeFrame(channel, Frame.Ping("Ping!".toByteArray()))
            writeFrame(channel, Frame.Text(true, "Foo".toByteArray()))
            assertIs<Frame.Text>(reader.incoming.receive())
            assertIs<Frame.Ping>(reader.incoming.receive())

            assertFailsWith<ProtocolViolationException> {
                // serializer will re-send the opcode
                reader.incoming.receive()
            }
        }
    }
}
