/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.math.*
import kotlin.test.*

class HttpRequestRetryTest {

    @Test
    fun test3RetriesWithExponentialDelayByDefault() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }

            val expectedDelays = listOf(1000L, 2000L, 4000L)
            assertEquals(expectedDelays.size, delays.size)

            expectedDelays.zip(delays).forEach { (expected, actual) ->
                assertTrue { actual in expected..expected + 1000 }
            }
        }
    }

    @Test
    fun testRetryOnNonSuccess() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testModifyRequest() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    assertEquals(null, it.headers["X-RETRY-COUNT"])
                    respondError(HttpStatusCode.InternalServerError)
                }
                addHandler {
                    assertEquals("1", it.headers["X-RETRY-COUNT"])
                    respondError(HttpStatusCode.InternalServerError)
                }
                addHandler {
                    assertEquals("2", it.headers["X-RETRY-COUNT"])
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
                addHandler {
                    assertEquals("3", it.headers["X-RETRY-COUNT"])
                    respondOk()
                }
            }
            install(HttpRequestRetry) {
                modifyRequest { it.headers.append("X-RETRY-COUNT", retryCount.toString()) }
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testExponentialDelayDefaults() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                exponentialDelay(randomizationMs = 0)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(1000L, 2000L, 4000L), delays)
        }
    }

    @Test
    fun testExponentialDelayExplicitBaseDelay() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                exponentialDelay(randomizationMs = 0, baseDelayMs = 200)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(200L, 400L, 800L), delays)
        }
    }

    @Test
    fun testExponentialDelayExplicitBase() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                exponentialDelay(randomizationMs = 0, base = 1.1)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(1000L, 1100L, 1210L), delays)
        }
    }

    @Test
    fun testConstantDelay() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                constantDelay(millis = 123, randomizationMs = 0)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(123L, 123L, 123L), delays)
        }
    }

    @Test
    fun testRetryAfterHeader() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler {
                    respondError(
                        HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.RetryAfter, "2")
                    )
                }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler {
                    respondError(
                        HttpStatusCode.InternalServerError,
                        // smaller than a client delay, should be ignored
                        headers = headersOf(HttpHeaders.RetryAfter, "1")
                    )
                }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                delayMillis { 1234 }
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(2000L, 1234L, 1234L), delays)
        }
    }

    @Test
    fun testRetryAfterHeaderFalse() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler {
                    respondError(
                        HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.RetryAfter, "1234")
                    )
                }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler {
                    respondError(
                        HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.RetryAfter, "2345")
                    )
                }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                delayMillis(false) { 123 }
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(123L, 123L, 123L), delays)
        }
    }

    @Test
    fun testMaxRetries() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(2)
                delay { }
            }
        }

        test { client ->
            assertFailsWith<ServerResponseException> {
                client.get { }
            }
        }
    }

    @Test
    fun testRetryOnException() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnException(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testRetryOnExceptionOrServerError() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { throw IOException("Another error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testRetryCanCancel() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
            }
        }

        test { client ->
            val request = HttpRequestBuilder()
            val context = request.executionContext
            GlobalScope.launch {
                context.cancel()
            }
            assertFailsWith<CancellationException> { client.get(request) }
        }
    }

    @Test
    fun testRetryPerRequestConfig() = testWithEngine(MockEngine) {
        val delays = mutableListOf<Long>()
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                noRetry()
                exponentialDelay()
                delay { delays.add(it) }
            }
        }

        test { client ->
            val response = client.get {
                retry {
                    constantDelay(millis = 123, randomizationMs = 0)
                    retryOnExceptionOrServerErrors(4)
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(123L, 123L, 123L, 123L), delays)
        }
    }

    @Test
    fun testRetryEvent() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val events = mutableListOf<HttpRetryEventData>()
            client.monitor.subscribe(HttpRequestRetryEvent) { events.add(it) }

            client.get {}

            assertEquals(2, events.size)

            assertEquals("Network error", events[0].cause?.message)
            assertEquals(1, events[0].retryCount)
            assertEquals(null, events[0].response)

            assertEquals(null, events[1].cause)
            assertEquals(2, events[1].retryCount)
            assertEquals(HttpStatusCode.InternalServerError, events[1].response?.status)
        }
    }

    @Test
    fun testRetryOnExceptionDoesntRetryTimeoutByDefault() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    delay(5000)
                    fail("Should not be called")
                }
            }
            install(HttpRequestRetry) {
                retryOnException(3)
                delay { }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 1000
            }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get {}
            }
        }
    }

    @Test
    fun testRetryOnExceptionRetriesTimeoutIfSet() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    delay(5000)
                    fail("Should not be called")
                }
                addHandler {
                    respondOk()
                }
            }
            install(HttpRequestRetry) {
                retryOnException(3, retryOnTimeout = true)
                delay { }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 1000
            }
        }

        test { client ->
            val response = client.get {}
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
