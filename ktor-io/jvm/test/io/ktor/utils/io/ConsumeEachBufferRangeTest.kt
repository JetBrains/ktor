/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.nio.*
import kotlin.test.*

@Suppress("PublicApiImplicitType")
@CoroutinesTimeout(10_000, cancelOnTimeout = true)
class ConsumeEachBufferRangeTest {
    private val content = ByteArray(16384) { it.toByte() }
    private val channel = ByteChannel(autoFlush = true)

    @Test
    fun test() {
        runBlocking {
            launch {
                var pos = 0
                while (pos < content.size) {
                    val size = (content.size - pos).coerceAtMost(526)
                    channel.writeFully(content, pos, size)
                    pos += size
                }
                channel.close()
            }

            val data = ByteArrayOutputStream()
            val tmp = ByteArray(8192)

            flow<ByteBuffer> {
                channel.consumeEachBufferRange { buffer, _ ->
                    emit(buffer)
                    true
                }
            }.collect { buffer ->
                val size = buffer.remaining()
                buffer.get(tmp, 0, size)
                data.write(tmp, 0, size)
            }

            val result: ByteArray = data.toByteArray()
            assertTrue(result.contentEquals(content))
        }
    }
}
