/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

private const val TEST_URL = "$TEST_SERVER/timeout"

class HttpTimeoutTest : ClientLoader() {
    @Test
    fun testGet() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val response = client.get("$TEST_URL/with-delay") {
                parameter("delay", 20)
            }.body<String>()
            assertEquals("Text", response)
        }
    }

    @Test
    fun testGetWithExceptionAndTryAgain() = clientTests {
        config {
            expectSuccess = true
        }
        test { client ->
            val requestBuilder = HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url("$TEST_URL/404")
                parameter("delay", 20)
            }

            val job = requestBuilder.executionContext
            assertTrue { job.isActive }

            assertFails { client.request(requestBuilder).body<String>() }
            assertTrue { job.isActive }

            requestBuilder.url("$TEST_URL/with-delay")

            val response = client.request(requestBuilder).body<String>()

            assertEquals("Text", response)
            assertTrue { job.isActive }
        }
    }

    @Test
    fun testWithExternalTimeout() = clientTests(listOf("Android")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val requestBuilder = HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url("$TEST_URL/with-delay")
                parameter("delay", 60000)
            }

            val exception = assertFails {
                withTimeout(500) {
                    client.request(requestBuilder).body<String>()
                }
            }

            assertTrue { exception is TimeoutCancellationException }
            assertTrue { requestBuilder.executionContext.getActiveChildren().none() }
        }
    }

    @Test
    fun testHead() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.head("$TEST_URL/with-delay?delay=10")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testHeadWithTimeout() = clientTests {
        config {
            install(HttpTimeout) {
                requestTimeoutMillis = 500
            }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.head("$TEST_URL/with-delay?delay=1000")
            }
        }
    }

    @Test
    fun testGetWithCancellation() = clientTests {
        config {
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }

            test { client ->
                val requestBuilder = HttpRequestBuilder().apply {
                    method = HttpMethod.Get
                    url("$TEST_URL/with-stream")
                    parameter("delay", 7000)
                }

                client.prepareRequest(requestBuilder).body<ByteReadChannel>().cancel()

                waitForCondition("all children to be cancelled") {
                    requestBuilder.executionContext.getActiveChildren().none()
                }
            }
        }
    }

    @Test
    fun testGetRequestTimeout() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 10 }
        }

        test { client ->
            assertFails {
                client.get("$TEST_URL/with-delay") {
                    parameter("delay", 5000)
                }.body<String>()
            }
        }
    }

    @Test
    fun testGetRequestTimeoutPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFails {
                client.get("$TEST_URL/with-delay") {
                    parameter("delay", 5000)

                    timeout { requestTimeoutMillis = 10 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testGetWithSeparateReceive() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val response = client.request("$TEST_URL/with-delay") {
                method = HttpMethod.Get
                parameter("delay", 10)
            }
            val result: String = response.body()

            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetWithSeparateReceivePerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.request("$TEST_URL/with-delay") {
                method = HttpMethod.Get
                parameter("delay", 10)

                timeout { requestTimeoutMillis = 1000 }
            }
            val result: String = response.body()

            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetRequestTimeoutWithSeparateReceive() = clientTests(listOf("Js")) {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val response = client.prepareRequest("$TEST_URL/with-stream") {
                method = HttpMethod.Get
                parameter("delay", 500)
            }.body<ByteReadChannel>()

            assertFailsWith<CancellationException> {
                response.readUTF8Line()
            }
        }
    }

    @Test
    fun testGetRequestTimeoutWithSeparateReceivePerRequestAttributes() =
        clientTests(listOf("Js", "Curl", "Darwin", "DarwinLegacy")) {
            config {
                install(HttpTimeout)
            }

            test { client ->
                val response = client.prepareRequest("$TEST_URL/with-stream") {
                    method = HttpMethod.Get
                    parameter("delay", 10000)

                    timeout { requestTimeoutMillis = 1000 }
                }.body<ByteReadChannel>()
                assertFailsWith<CancellationException> {
                    response.readUTF8Line()
                }
            }
        }

    @Test
    fun testGetAfterTimeout() = clientTests(listOf("Curl", "Js", "Darwin", "DarwinLegacy")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.prepareGet("$TEST_URL/with-stream") {
                parameter("delay", 10000)
                timeout { requestTimeoutMillis = 1000 }
            }.body<ByteReadChannel>()
            assertFailsWith<CancellationException> {
                response.readUTF8Line()
            }
            val result = client.get("$TEST_URL/with-delay?delay=1") {
                timeout { requestTimeoutMillis = 10000 }
            }.bodyAsText()
            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetStream() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val response = client.get("$TEST_URL/with-stream") {
                parameter("delay", 10)
            }.body<ByteArray>()

            assertEquals("Text", response.decodeToString(0, 0 + response.size))
        }
    }

    @Test
    fun testGetStreamPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.get("$TEST_URL/with-stream") {
                parameter("delay", 10)

                timeout { requestTimeoutMillis = 1000 }
            }.body<ByteArray>()

            assertEquals("Text", response.decodeToString(0, 0 + response.size))
        }
    }

    @Test
    fun testGetStreamRequestTimeout() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 4000)
                }.body<ByteArray>()
            }
        }
    }

    @Test
    fun testGetStreamRequestTimeoutPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 400)

                    timeout { requestTimeoutMillis = 1000 }
                }.body<ByteArray>()
            }
        }
    }

    // Js can't configure test timeout in browser
    @Test
    fun testRedirect() = clientTests(listOf("js")) {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 10000 }
        }

        test { client ->
            val response = client.get("$TEST_URL/with-redirect") {
                parameter("delay", 20)
                parameter("count", 2)
            }.body<String>()

            assertEquals("Text", response)
        }
    }

    // Js can't configure test timeout in browser
    @Test
    fun testRedirectPerRequestAttributes() = clientTests(listOf("js")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.get("$TEST_URL/with-redirect") {
                parameter("delay", 20)
                parameter("count", 2)

                timeout { requestTimeoutMillis = 10000 }
            }.body<String>()
            assertEquals("Text", response)
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnFirstStep() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 20 }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 1000)
                    parameter("count", 5)
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnFirstStepPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 1000)
                    parameter("count", 5)

                    timeout { requestTimeoutMillis = 20 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnSecondStep() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 400 }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 500)
                    parameter("count", 5)
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnSecondStepPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 500)
                    parameter("count", 5)

                    timeout { requestTimeoutMillis = 400 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testConnectionRefusedException() = clientTests(listOf("Js", "native:*", "win:*")) {
        config {
            install(HttpTimeout) { connectTimeoutMillis = 1000 }
        }

        test { client ->
            assertFails {
                try {
                    client.get("http://localhost:11").body<String>()
                } catch (_: ConnectTimeoutException) {
                    /* Ignore. */
                }
            }
        }
    }

    @Test
    fun testSocketTimeoutRead() = clientTests(listOf("Js", "native:CIO", "Java")) {
        config {
            install(HttpTimeout) { socketTimeoutMillis = 1000 }
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 5000)
                }.body<String>()
            }
        }
    }

    @Test
    fun testSocketTimeoutReadPerRequestAttributes() = clientTests(listOf("Js", "native:CIO", "Java", "Apache5")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 5000)

                    timeout { socketTimeoutMillis = 1000 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testSocketTimeoutWriteFailOnWrite() = clientTests(listOf("Js", "Android", "native:CIO", "Java")) {
        config {
            install(HttpTimeout) { socketTimeoutMillis = 500 }
        }

        test { client ->
            assertFailsWith<SocketTimeoutException> {
                client.post("$TEST_URL/slow-read") { setBody(makeString(4 * 1024 * 1024)) }
            }
        }
    }

    @Test
    fun testSocketTimeoutWriteFailOnWritePerRequestAttributes() = clientTests(
        listOf("Js", "Android", "Apache5", "native:CIO", "Java")
    ) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<SocketTimeoutException> {
                client.post("$TEST_URL/slow-read") {
                    setBody(makeString(4 * 1024 * 1024))
                    timeout { socketTimeoutMillis = 500 }
                }
            }
        }
    }

    @Test
    fun testNonPositiveTimeout() {
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                requestTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                requestTimeoutMillis = 0
            )
        }

        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                socketTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                socketTimeoutMillis = 0
            )
        }

        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                connectTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                connectTimeoutMillis = 0
            )
        }
    }
}
