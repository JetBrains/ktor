/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

class DefaultTransformTest {

    @Test
    fun testReadingHeadResponseAsByteArray() = testSuspend {
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond("", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }

        httpClient.head("http://host/path").body<ByteArray>()
    }
}
