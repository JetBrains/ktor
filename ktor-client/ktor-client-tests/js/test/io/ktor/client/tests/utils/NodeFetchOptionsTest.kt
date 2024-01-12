// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class NodeFetchOptionsTest {

    @Test
    fun testNodeOptions() = testSuspend {
        // Custom nodeOptions only work on Node.js (as the name suggests ;)
        if (PlatformUtils.IS_BROWSER) return@testSuspend

        val client = HttpClient(Js) {
            engine {
                nodeOptions.headers = js("""{"Content-Type": "image/jpeg"}""")
            }
        }
        val response = client.post("$TEST_SERVER/content-type") {
            // This header gets overridden by the nodeOptions
            header("Content-Type", "application/pdf")
        }.body<String>()
        assertEquals("image/jpeg", response)
    }

    @Test
    fun testDefault() = testSuspend {
        val client = HttpClient(Js)
        val response = client.post("$TEST_SERVER/content-type") {
            header("Content-Type", "application/pdf")
        }.body<String>()
        assertEquals("application/pdf", response)
    }
}
