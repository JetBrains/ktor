/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.server.engine.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.system.*
import kotlin.test.*

var count = 0

abstract class ConfigTestSuite(val engine: ApplicationEngineFactory<*, *>) {

    @Test
    fun testStartOnceWhenException() {
        // Please note that it's critical to not capture any variables inside the scope
        // It will disable autoreload and change behaviour
        val server = embeddedServer(engine) {
            count++
            error("Foo")
        }

        assertFailsWith<IllegalStateException> {
            server.start(wait = false)
        }

        assertEquals(1, count)
        server.stop(1, 1, TimeUnit.SECONDS)
    }

    @Test
    fun testStartsOnceWithCapture() {
        var counter = 0
        val server = embeddedServer(engine) {
            counter++
            error("Foo")
        }

        assertFailsWith<IllegalStateException> {
            server.start(wait = false)
        }

        assertEquals(1, counter)
        server.stop(1, 1, TimeUnit.SECONDS)
    }

    @Test
    fun testFastStop() = runBlocking {
        val server = embeddedServer(engine) {
        }

        val time = measureTimeMillis {
            server.stop(0, 100, TimeUnit.SECONDS)
        }

        assertTrue(time < 100, "Stop time is $time")
    }
}
