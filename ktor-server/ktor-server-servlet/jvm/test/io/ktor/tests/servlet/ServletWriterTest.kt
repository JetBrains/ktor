/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.servlet

import io.ktor.server.servlet.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import kotlin.test.Test
import kotlin.test.assertEquals

class ServletWriterTest {

    @Test
    fun testWriteBufferWithPositivePosition() = runBlocking {
        val content = withServletWriter {
            val buffer = ByteReadPacket("1234567890".toByteArray())
            buffer.readByte()

            writeBuffer(buffer)
            flushAndClose()
        }

        assertEquals("234567890", content.decodeToString())
    }

    suspend fun withServletWriter(block: suspend ByteWriteChannel.() -> Unit): ByteArray {
        val content = BytePacketBuilder()
        val output = object : ServletOutputStream() {
            override fun isReady(): Boolean = true

            override fun setWriteListener(writeListener: WriteListener) {
                writeListener.onWritePossible()
            }

            override fun write(b: Int) {
                content.writeByte((b and 0xff).toByte())
            }
        }

        coroutineScope {
            val channel = servletWriter(output).channel
            block(channel)
            channel.flushAndClose()
        }

        return content.build().readByteArray()
    }
}
