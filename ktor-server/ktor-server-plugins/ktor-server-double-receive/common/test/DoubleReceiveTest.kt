/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlin.coroutines.*
import kotlin.test.*

class DoubleReceiveTest {

    @Test
    fun testInMemoryCache() = runTest {
        val content = ByteArray(16 * 1024 * 1024) { it.toByte() }
        val cache = MemoryCache(
            ByteReadChannel(content),
            EmptyCoroutineContext
        )

        repeat(3) {
            val channel = cache.read()
            val received = channel.readRemaining().readByteArray()
            assertEquals(content.toList(), received.toList())
        }
    }
}
