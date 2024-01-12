/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.test.*

class BlockingAdaptersOnProhibitedThreadTest {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("BlockingAdaptersOnProhibitedThreadTest")

    @AfterTest
    fun cleanup() {
        dispatcher.close()
    }

    @Test
    fun testInputStreamAllowed() {
        runBlocking(dispatcher) {
            ByteChannel().toInputStream()
        }
    }

    @Test
    fun testInputStreamProhibitedNotFails() {
        runBlocking(dispatcher) {
            prohibitParking()
            ByteChannel().toInputStream()
        }
    }

    @Test
    fun testOutputStreamAllowed() {
        runBlocking(dispatcher) {
            ByteChannel().toOutputStream()
        }
    }

    @Test
    fun testOutputStreamCreatedOnNetworkThread() {
        runBlocking(dispatcher) {
            prohibitParking()
            ByteChannel().toOutputStream()
        }
    }
}
