/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*
import java.nio.*
import kotlin.test.*
import kotlin.test.Test

class ChunkedTest {
    @Test
    fun testEmptyBroken(): Unit = runBlocking {
        val bodyText = ""
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        assertThrows<EOFException> {
            decodeChunked(ch, parsed)
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testChunkedWithContentLength() = runBlocking {
        val chunkedContent = listOf(
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n",
        )

        val input = writer {
            chunkedContent.forEach {
                channel.writeStringUtf8(it)
            }
        }.channel

        val output = ByteChannel()
        launch {
            decodeChunked(input, output)
            output.close()
        }

        val content = output.readRemaining().readText()
        assertEquals("a=1", content)
    }

    @Test
    fun testEmptyWithoutCrLf(): Unit = runBlocking {
        val bodyText = "0"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        assertThrows<EOFException> {
            decodeChunked(ch, parsed)
        }
    }

    @Test
    fun testEmpty() = runBlocking {
        val bodyText = "0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals(0, parsed.availableForRead)
        assertTrue { parsed.isClosedForRead }
    }

    @Test
    fun testEmptyWithTrailing() = runBlocking {
        val bodyText = "0\r\n\r\ntrailing"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals(0, parsed.availableForRead)
        assertTrue { parsed.isClosedForRead }
        assertEquals("trailing", ch.readRemaining().readText())
    }

    @Test
    fun testContent() = runBlocking {
        val bodyText = "3\r\n123\r\n0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123", parsed.readUTF8Line())
    }

    @Test
    fun testContentMultipleChunks() = runBlocking {
        val bodyText = "3\r\n123\r\n2\r\n45\r\n1\r\n6\r\n0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123456", parsed.readUTF8Line())
    }

    @Test
    fun testContentMixedLineEndings() = runBlocking {
        val bodyText = "3\n123\n2\r\n45\r\n1\r6\r0\r\n\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123456", parsed.readUTF8Line())
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testEncodeEmpty() = runBlocking {
        val encoded = ByteChannel()

        launch(coroutineContext) {
            try {
                encodeChunked(encoded, ByteReadChannel.Empty)
            } finally {
                encoded.close()
            }
        }

        yield()
        val encodedText = encoded.readRemaining().inputStream().reader().readText()
        assertEquals("0\r\n\r\n", encodedText)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testEncodeChunks() = runBlocking {
        val output = ByteChannel(false)
        val encoded = ByteChannel()

        launch {
            try {
                encodeChunked(encoded, output)
            } finally {
                encoded.close()
            }
        }

        yield()
        output.writeStringUtf8("123")
        yield()
        output.writeStringUtf8("45")
        yield()
        output.writeStringUtf8("6")
        output.close()
        yield()

        val encodedText = encoded.readRemaining().inputStream().reader().readText()
        assertEquals("6\r\n123456\r\n0\r\n\r\n", encodedText)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun longLoop() = runBlocking {
        val content = ByteChannel(true)
        val encoded = ByteChannel()
        val decoded = ByteChannel()

        val written = CompletableDeferred<String>()
        val read = CompletableDeferred<String>()

        launch(coroutineContext) {
            val sb = StringBuilder()
            repeat(7000) {
                val s = "Ab7CdEfZ".take(it + 1)
                sb.append(s)
                content.writeStringUtf8(s)
            }
            content.close()
            written.complete(sb.toString())
        }

        launch(coroutineContext) {
            try {
                encodeChunked(encoded, content)
            } finally {
                encoded.close()
            }
        }

        launch(coroutineContext) {
            decodeChunked(encoded, decoded)
        }

        launch(coroutineContext) {
            try {
                val sb = StringBuilder()
                val bb = ByteBuffer.allocate(256)

                while (true) {
                    bb.clear()
                    val rc = decoded.readAvailable(bb)
                    if (rc == -1) break
                    bb.flip()
                    val text = String(bb.array(), 0, bb.remaining())

                    sb.append(text)
                }

                read.complete(sb.toString())
            } catch (t: Throwable) {
                read.completeExceptionally(t)
            }
        }

        val first = written.await()
        val second = read.await()

        assertEquals(first, second)
    }
}
