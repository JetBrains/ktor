/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.test.*

class ByteChannelNativeConcurrentTest {
    private val TEST_SIZE = 10 * 1024

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReadWriteByte() {
        val channel = ByteChannel()
        GlobalScope.launch {
            repeat(TEST_SIZE) {
                channel.writeByte(it.toByte())
            }

            channel.flush()
        }

        runBlocking {
            repeat(TEST_SIZE) {
                assertEquals(it.toByte(), channel.readByte())
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReadWriteBlock() {
        val channel = ByteChannel()
        val BLOCK_SIZE = 1024
        val block = createBlock(BLOCK_SIZE)
        GlobalScope.launch {
            repeat(TEST_SIZE) {
                channel.writeFully(block)
            }

            channel.flush()
        }

        runBlocking {
            repeat(TEST_SIZE) {
                val result = channel.readRemaining(BLOCK_SIZE.toLong())

                assertTrue {
                    block.contentEquals(result.readBytes())
                }
            }
        }
    }

    private fun createBlock(size: Int): ByteArray = ByteArray(size) { it.toByte() }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test
    fun testFlushBeforeAndAfterWrite() = runBlocking {
        val latch = Channel<Unit>()
        val channel = ByteChannel()

        val dispatcher1 = newSingleThreadContext("test-1")
        val dispatcher2 = newSingleThreadContext("test-2")
        try {
            val first = launch(dispatcher1) {
                repeat(1000) {
                    latch.send(Unit)
                    channel.readByte()
                }
            }
            val second = launch(dispatcher2) {
                repeat(1000) {
                    latch.receive()
                    channel.flush()
                    channel.writeByte(42)
                    channel.flush()
                }
            }

            first.join()
            second.join()
        } finally {
            dispatcher1.close()
            dispatcher2.close()
        }
    }
}
