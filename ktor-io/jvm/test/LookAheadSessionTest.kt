import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class LookAheadSessionTest {

    @Test
    fun testAwait() = runBlocking {
        val channel = writer {
            repeat(100) {
                channel.writeInt(it)
                channel.flush()
            }
        }.channel

        channel.lookAheadSuspend {
            repeat(100) {
                var buffer = request(0, 4)
                if (buffer == null) {
                    awaitAtLeast(4)
                    buffer = request(0, 4)
                }

                assertEquals(it, buffer!!.getInt())
                consumed(4)
            }
        }
    }
}
