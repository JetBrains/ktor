/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.lang.reflect.*
import kotlin.test.*

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(DelicateCoroutinesApi::class)
class BlockingContentParkingTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("BlockingContentParkingTest")
    private val channel = ByteChannel(true)
    private val consumer = GlobalScope.launch {
        channel.discard()
    }

    @AfterTest
    fun cleanup() {
        channel.cancel()
        consumer.cancel()
        dispatcher.close()
    }

    @Test
    fun testOutputStreamContentAllowed() {
        val content = OutputStreamContent(
            {
                ensureNotProhibited()
                write(ByteArray(99999))
            },
            ContentType.Application.OctetStream
        )

        testOnThread {
            content.writeTo(channel)
        }
    }

    @Test
    fun testOutputStreamContentProhibited() {
        val content = OutputStreamContent(
            {
                ensureNotProhibited()
                write(ByteArray(99999))
            },
            ContentType.Application.OctetStream
        )

        testOnThread {
            markParkingProhibited()
            content.writeTo(channel)
        }
    }

    @Test
    fun testWriterContentAllowed() {
        val content = WriterContent(
            {
                ensureNotProhibited()
                write(CharArray(99999))
            },
            ContentType.Application.OctetStream
        )

        testOnThread {
            content.writeTo(channel)
        }
    }

    @Test
    fun testWriterContentProhibited() {
        val content = WriterContent(
            {
                ensureNotProhibited()
                write(CharArray(99999))
            },
            ContentType.Application.OctetStream
        )

        testOnThread {
            markParkingProhibited()
            content.writeTo(channel)
        }
    }

    private fun testOnThread(
        block: suspend () -> Unit
    ) {
        runBlocking(dispatcher) {
            block()
        }
    }

    private val prohibitParkingFunction: Method? by lazy {
        Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
            .getMethod("prohibitParking")
    }

    private fun markParkingProhibited() {
        prohibitParkingFunction?.invoke(null)
    }

    private fun ensureNotProhibited() {
        check(
            Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
                .getMethod("isParkingAllowed")
                .invoke(null) == true
        ) { "Parking is now allowed here." }
    }
}
