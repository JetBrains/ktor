/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DefaultTransformTest {

    @Test
    fun testReadingHeadResponseAsByteArray() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond("", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }
        client.head("http://host/path").body<ByteArray>()
    }
}
