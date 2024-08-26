/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class PostTest : ClientLoader() {
    @Test
    fun testPostString() = clientTests(listOf("Js")) {
        test { client ->
            client.postHelper(makeString(777))
        }
    }

    @Test
    fun testHugePost() = clientTests(listOf("Js", "Darwin", "CIO", "Curl", "DarwinLegacy", "WinHttp")) {
        test { client ->
            client.postHelper(makeString(32 * 1024 * 1024))
        }
    }

    @Test
    fun testWithPause() = clientTests(listOf("Js", "Darwin", "CIO", "DarwinLegacy")) {
        config {
            install(HttpTimeout) {
                socketTimeoutMillis = 1.minutes.inWholeMilliseconds
            }
        }
        test { client ->
            val content = makeString(16 * 1024)

            val response = client.post("$TEST_SERVER/content/echo") {
                setBody(
                    object : OutgoingContent.WriteChannelContent() {

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8(content)
                            delay(1000)
                            channel.writeStringUtf8(content)
                            channel.flushAndClose()
                        }
                    }
                )
            }.body<String>()

            assertEquals(content + content, response)
        }
    }

    private suspend fun HttpClient.postHelper(text: String) {
        val response = post("$TEST_SERVER/content/echo") {
            setBody(text)
        }.body<String>()
        assertEquals(text, response)
    }
}
