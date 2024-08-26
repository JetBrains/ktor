/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

class HttpStatementTest : ClientLoader() {

    @Test
    @Ignore
    fun testExecute() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
                val expected = buildPacket {
                    repeat(42) {
                        writeInt(42)
                    }
                }.readByteArray(42)

                val actual = it.readBytes(42)

                assertArrayEquals("Invalid content", expected, actual)
            }

            val response = client.prepareGet("$TEST_SERVER/content/hello").execute()
            assertEquals("hello", response.body())
        }
    }

    @Test
    fun testGZipFromSavedResponse() = clientTests(listOf("native:CIO")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_SERVER/compression/gzip")
            assertTrue(response.coroutineContext[Job]!!.isCompleted)

            val content = response.body<String>()
            assertEquals("Compressed response!", content)
        }
    }

    @Test
    fun testJobFinishedAfterResponseRead() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/hello").execute().apply {
                assertTrue(call.coroutineContext.job.isCompleted)
            }

            client.prepareGet("$TEST_SERVER/content/hello").execute {
                assertFalse(it.call.coroutineContext.job.isCompleted)
                it
            }.apply {
                assertTrue(call.coroutineContext.job.isCompleted)
            }
        }
    }
}
