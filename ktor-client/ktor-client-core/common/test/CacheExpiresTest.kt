/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.client.plugins.cache.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*
import kotlin.test.*

class CacheExpiresTest {
    @Test
    fun testValidExpirationDate() {
        val dateText = "Tue, 27 Oct 2020 15:21:07 GMT"
        val parsed = dateText.fromHttpToGmtDate()

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires(false)
        assertEquals(parsed, result)
    }

    @Test
    fun testInvalidExpirationDate() {
        val dateText = "A1231242323532452345"
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires(false) { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateZero() {
        val dateText = "0"
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires(false) { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateEmpty() {
        val dateText = ""
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires(false) { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateBlank() {
        val dateText = " "
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires(false) { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testMaxAgePrivate() {
        val now = GMTDate(10)
        val response = response(now) {
            append(HttpHeaders.CacheControl, "s-maxage=5, max-age=15")
        }

        val result = response.cacheExpires(false)
        assertEquals(GMTDate(now.timestamp + 15 * 1000), result)
    }

    @Test
    fun testMaxAgeShared() {
        val now = GMTDate(10)
        val response = response(now) {
            append(HttpHeaders.CacheControl, "s-maxage=5, max-age=15")
        }

        val result = response.cacheExpires(true)
        assertEquals(GMTDate(now.timestamp + 5 * 1000), result)
    }

    @Test
    fun testMaxAgeSharedNoSMaxAge() {
        val now = GMTDate(10)
        val response = response(now) {
            append(HttpHeaders.CacheControl, "max-age=15")
        }

        val result = response.cacheExpires(true)
        assertEquals(GMTDate(now.timestamp + 15 * 1000), result)
    }

    private fun response(requestTime: GMTDate = GMTDate(), builder: HeadersBuilder.() -> Unit): HttpResponse {
        return Response(buildHeaders(builder), requestTime)
    }

    private class Response(
        override val headers: Headers,
        override val requestTime: GMTDate = GMTDate()
    ) : HttpResponse() {
        override val call: HttpClientCall get() = error("Shouldn't be used")
        override val status: HttpStatusCode
            get() = error("Shouldn't be used")
        override val version: HttpProtocolVersion
            get() = error("Shouldn't be used")
        override val responseTime: GMTDate
            get() = error("Shouldn't be used")

        @OptIn(InternalAPI::class)
        override val rawContent: ByteReadChannel
            get() = error("Shouldn't be used")
        override val coroutineContext: CoroutineContext
            get() = error("Shouldn't be used")
    }
}
