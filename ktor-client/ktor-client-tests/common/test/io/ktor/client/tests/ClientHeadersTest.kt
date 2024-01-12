/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class ClientHeadersTest : ClientLoader() {

    @Test
    fun testHeadersReturnNullWhenMissing() = clientTests(
        listOf("Java", "Curl", "Js", "Darwin", "DarwinLegacy", "WinHttp")
    ) {
        test { client ->
            client.get("$TEST_SERVER/headers").let {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("", it.bodyAsText())

                assertNull(it.headers["X-Nonexistent-Header"])
                assertNull(it.headers.getAll("X-Nonexistent-Header"))
            }
        }
    }

    @Test
    fun testContentNegotiationMediaType() = clientTests(listOf("Java", "Curl", "Js", "Darwin", "DarwinLegacy")) {
        test { client ->
            client.preparePost("$TEST_SERVER/content-type") {
                contentType(ContentType.Application.Json)
                setBody("123")
            }.execute {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("application/json", it.bodyAsText())
            }
        }
    }

    @Test
    fun testHeadersMerge() = clientTests(listOf("Js")) {
        test { client ->
            client.get("$TEST_SERVER/headers-merge") {
                accept(ContentType.Text.Html)
                accept(ContentType.Application.Json)
            }.let {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("JSON", it.bodyAsText())
                assertEquals("application/json", it.headers[HttpHeaders.ContentType])
            }

            client.get("$TEST_SERVER/headers-merge") {
                accept(ContentType.Text.Html)
                accept(ContentType.Application.Xml)
            }.let {
                assertEquals("XML", it.bodyAsText())
                assertEquals("application/xml", it.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testAcceptMerge() = clientTests(listOf("Js")) {
        test { client ->
            val lines = client.get("$TCP_SERVER/headers-merge") {
                accept(ContentType.Application.Xml)
                accept(ContentType.Application.Json)
            }.body<String>().split("\n")

            val acceptHeaderLine = lines.first { it.startsWith("Accept:") }
            assertEquals("Accept: application/xml,application/json", acceptHeaderLine)
        }
    }

    @Test
    fun testSingleHostHeader() = clientTests(listOf("Js", "Android", "Java")) {
        test { client ->
            client.get("$TEST_SERVER/headers/host") {
                header(HttpHeaders.Host, "CustomHost")
            }
        }
    }

    @Test
    fun testUnsafeHeaders() = clientTests {
        test { client ->
            var message = ""
            try {
                client.get("$TEST_SERVER/headers") {
                    header(HttpHeaders.ContentLength, 0)
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(HttpHeaders.TransferEncoding, "chunked")
                    header(HttpHeaders.Upgrade, "upgrade")
                }
            } catch (cause: UnsafeHeaderException) {
                message = cause.message ?: ""
            }

            val expected = "Header(s) ${HttpHeaders.UnsafeHeadersList.filter { it != HttpHeaders.ContentType }} " +
                "are controlled by the engine and cannot be set explicitly"

            assertEquals(expected, message)
        }
    }

    @Test
    fun testRequestHasContentLength() = clientTests(listOf("Java", "Curl", "Js", "Darwin", "DarwinLegacy", "WinHttp")) {
        test { client ->
            val get = client.get("$TEST_SERVER/headers").bodyAsText()
            assertEquals("", get)

            val head = client.head("$TEST_SERVER/headers").bodyAsText()
            assertEquals("", head)

            val put = client.put("$TEST_SERVER/headers").bodyAsText()
            assertEquals("0", put)

            val post = client.post("$TEST_SERVER/headers").bodyAsText()
            assertEquals("0", post)
        }
    }
}
