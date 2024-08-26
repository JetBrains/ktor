import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ReadUtf8LineTest {

    @Test
    fun testReadUtf8LineWithLongLineWithLimit() = testSuspend {
        val lineSize = 1024
        val line = "A".repeat(lineSize)

        val channel = writer {
            repeat(10) {
                channel.writeStringUtf8(line)
            }
        }.channel

        val builder = StringBuilder()
        channel.readUTF8LineTo(builder, 8 * 1024)
        assertEquals(8 * 1024, builder.length)
        assertEquals("A".repeat(8 * 1024), builder.toString())
    }

    @Test
    fun testNewLineAfterFlush() = testSuspend {
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\n2\r\n")
        }.channel

        val buffer = StringBuilder()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("4", buffer.toString())
        buffer.clear()

        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("2", buffer.toString())
    }

    @Test
    fun testFlushBeforeNewLine() = testSuspend {
        val channel = writer {
            channel.writeStringUtf8("4")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\r\n2\r\n")
        }.channel

        val buffer = StringBuilder()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("4", buffer.toString())
        buffer.clear()

        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("2", buffer.toString())
    }
}
