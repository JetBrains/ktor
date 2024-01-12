/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import java.io.*
import java.nio.*
import java.util.zip.*
import kotlin.random.*
import kotlin.test.*
import kotlin.test.Test

@CoroutinesTimeout(60_000)
class DeflaterReadChannelTest : CoroutineScope {
    private val testJob = Job()
    override val coroutineContext get() = testJob + Dispatchers.Unconfined

    @AfterTest
    fun after() {
        testJob.cancel()
    }

    @Test
    fun testWithRealFile() {
        val file = listOf(
            File("jvm/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt"),
            File("ktor-server/ktor-server-tests/jvm/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt")
        ).first(File::exists)

        testReadChannel(file.readText(), file.readChannel())
        testWriteChannel(file.readText(), file.readChannel())
    }

    @Test
    fun testFileChannel() {
        val file = listOf(
            File("jvm/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt"),
            File("ktor-server/ktor-server-tests/jvm/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt")
        ).first(File::exists)

        val content = file.readText()

        fun read(from: Long, to: Long) = file.readChannel(from, to).toInputStream().reader().readText()

        assertEquals(content.take(3), read(0, 2))
        assertEquals(content.drop(1).take(2), read(1, 2))
        assertEquals(content.takeLast(3), read(file.length() - 3, file.length() - 1))
    }

    @Test
    fun testSmallPieces() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals(text, asyncOf(text).toInputStream().reader().readText())

        for (step in 1..text.length) {
            testReadChannel(text, asyncOf(text))
            testWriteChannel(text, asyncOf(text))
        }
    }

    @Test
    fun testBiggerThan8k() {
        val text = buildString {
            while (length < 65536) {
                append("The quick brown fox jumps over the lazy dog")
            }
        }
        val bb = ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1))

        for (
        step in generateSequence(1) { it * 2 }
            .dropWhile { it < 64 }
            .takeWhile { it <= 8192 }
            .flatMap { sequenceOf(it, it - 1, it + 1) }
        ) {
            bb.clear()
            testReadChannel(text, asyncOf(bb))

            bb.clear()
            testWriteChannel(text, asyncOf(bb))
        }
    }

    @Test
    fun testLargeContent() {
        val text = buildString {
            for (i in 1..16384) {
                append("test$i\n".padStart(10, ' '))
            }
        }

        testReadChannel(text, asyncOf(text))
        testWriteChannel(text, asyncOf(text))
    }

    @Test
    fun testGzippedBiggerThan8k() {
        val text = buildString {
            for (i in 1..65536) {
                append(' ' + Random.nextInt(32, 126) % 32)
            }
        }

        testReadChannel(text, asyncOf(text))
        testWriteChannel(text, asyncOf(text))
    }

    @Test
    fun testFaultyGzippedBiggerThan8k() {
        val text = buildString {
            for (i in 1..65536) {
                append(' ' + Random.nextInt(32, 126) % 32)
            }
        }

        testFaultyWriteChannel(asyncOf(text))
    }

    private fun asyncOf(text: String): ByteReadChannel = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)))
    private fun asyncOf(bb: ByteBuffer): ByteReadChannel = ByteReadChannel(bb)

    private fun InputStream.ungzip() = GZIPInputStream(this)

    private fun testReadChannel(expected: String, src: ByteReadChannel) {
        assertEquals(expected, src.deflated().toInputStream().ungzip().reader().readText())
    }

    private fun testWriteChannel(expected: String, src: ByteReadChannel) {
        val channel = ByteChannel(true)
        launch {
            src.copyAndClose((channel as ByteWriteChannel).deflated())
        }

        val result = channel.toInputStream().ungzip().reader().readText()
        assertEquals(expected, result)
    }

    private fun testFaultyWriteChannel(src: ByteReadChannel) = runBlocking {
        var deflateInputChannel: ByteWriteChannel?

        withContext(Dispatchers.IO) {
            val channel = ByteChannel(true)
            deflateInputChannel = (channel as ByteWriteChannel).deflated(coroutineContext = coroutineContext)

            // The copy operation will throw the IOException, but in order to simulate a real scenario
            // we don't want it to stop the execution, we want the deflating process to become aware of the error,
            // i.e. that it doesn't have an output channel on which to write the gzipped data, so it should stop.
            launch {
                try {
                    src.copyAndClose(deflateInputChannel!!)
                } catch (_: Exception) {
                }
            }

            launch {
                channel.close(IOException("Broken pipe"))
            }
        }

        assertFailsWith(IOException::class) { throw deflateInputChannel!!.closedCause!! }
    }
}
