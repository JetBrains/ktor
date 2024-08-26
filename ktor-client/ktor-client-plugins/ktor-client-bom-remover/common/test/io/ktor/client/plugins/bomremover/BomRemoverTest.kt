/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.bomremover

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class BomRemoverTest : ClientLoader() {
    @Test
    fun testRemoveBomCorrectly() = clientTests {
        config {
            install(BOMRemover)
        }

        test { client ->
            client.get("$TEST_SERVER/bom/with-bom-utf8").apply {
                assertEquals("Hello world", bodyAsText())
            }
            client.get("$TEST_SERVER/bom/with-bom-utf16").apply {
                assertEquals("Hello world", bodyAsText())
            }
            client.get("$TEST_SERVER/bom/with-bom-utf32").apply {
                assertEquals("Hello world", bodyAsText())
            }
        }
    }

    @Test
    fun testNoBom() = clientTests {
        config {
            install(BOMRemover)
        }

        test { client ->
            client.get("$TEST_SERVER/bom/without-bom").apply {
                val body = body<ByteArray>()
                assertEquals(2, body.size)
                assertEquals(0xEF.toByte(), body[0])
                assertEquals(0xBB.toByte(), body[1])
            }
            client.get("$TEST_SERVER/bom/without-bom-short").apply {
                assertEquals("1", bodyAsText())
            }
            client.get("$TEST_SERVER/bom/without-bom-long").apply {
                assertEquals("Hello world", bodyAsText())
            }
        }
    }

    @Test
    fun testNoBody() = clientTests {
        config {
            install(BOMRemover)
        }

        test { client ->
            client.get("$TEST_SERVER/bom/empty-body").apply {
                assertTrue(bodyAsText().isEmpty())
            }
        }
    }
}
