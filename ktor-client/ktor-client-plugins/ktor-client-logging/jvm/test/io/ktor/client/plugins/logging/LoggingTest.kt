/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.logging

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlin.test.*

class LoggingTest {

    @Test
    fun testByteReadChannelTryReadTextShouldCompleteOnMalformed() = runBlocking {
        val channel = ByteReadChannel(byteArrayOf(-77, 111))
        val result = channel.tryReadText(Charsets.UTF_8)
        assertEquals("�o", result)
    }

    @Test
    fun testByteReadChannelTryReadTextShouldReturnText() = runBlocking {
        val channel = ByteReadChannel("test")
        val result = channel.tryReadText(Charsets.UTF_8)
        assertEquals(result, "test")
    }
}
