/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.tests.utils.*
import io.ktor.sse.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*
import kotlin.test.*

class ServerSentEventsTest : ClientLoader(timeoutSeconds = 120) {

    @Test
    fun testExceptionIfSseIsNotInstalled() = testSuspend {
        val client = HttpClient()
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEventsSession()
        }.let {
            kotlin.test.assertContains(it.message!!, SSE.key.name)
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEvents {}
        }.let {
            kotlin.test.assertContains(it.message!!, SSE.key.name)
        }
    }

    @Test
    fun testSseSession() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello")
            session.incoming.single().apply {
                assertEquals("0", id)
                assertEquals("hello 0", event)
                val lines = data?.lines() ?: emptyList()
                assertEquals(2, lines.size)
                assertEquals("hello", lines[0])
                assertEquals("from server", lines[1])
            }
            session.cancel()
        }
    }

    @Test
    fun testParallelSseSessions() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            coroutineScope {
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=100")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        val lines = it.data?.lines() ?: emptyList()
                        assertEquals(2, lines.size)
                        assertEquals("hello", lines[0])
                        assertEquals("from server", lines[1])
                        size++
                    }
                    assertEquals(100, size)
                    session.cancel()
                }
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=50")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        val lines = it.data?.lines() ?: emptyList()
                        assertEquals(2, lines.size)
                        assertEquals("hello", lines[0])
                        assertEquals("from server", lines[1])
                        size++
                    }
                    assertEquals(50, size)
                    session.cancel()
                }
            }
        }
    }

    @Test
    fun testSseSessionWithError() = clientTests(listOf("Darwin", "DarwinLegacy")) {
        config {
            install(SSE)
        }

        test { client ->
            kotlin.test.assertFailsWith<SSEException> {
                client.serverSentEventsSession("http://testerror.com/sse")
            }
        }
    }

    @Test
    fun testExceptionSse() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            kotlin.test.assertFailsWith<SSEException> {
                client.serverSentEvents("$TEST_SERVER/sse/hello") { error("error") }
            }.let {
                kotlin.test.assertContains(it.message!!, "error")
            }
        }
    }

    @Test
    fun testCancellationExceptionSse() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            coroutineScope {
                val job: Job
                suspendCoroutine { cont ->
                    job = launch {
                        client.serverSentEvents("$TEST_SERVER/sse/hello") {
                            cont.resume(Unit)
                            awaitCancellation()
                        }
                    }
                }
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun testNoCommentsByDefault() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    assertEquals("${i * 2 + 1}", it.data)
                    size++
                }
                assertEquals(50, size)
            }
        }
    }

    @Test
    fun testShowComments() = clientTests(listOf("OkHttp")) {
        config {
            install(SSE) {
                showCommentEvents()
            }
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    if (i % 2 == 0) {
                        assertEquals("$i", it.comments)
                    } else {
                        assertEquals("$i", it.data)
                    }
                    size++
                }
                assertEquals(100, size)
            }
        }
    }

    @Test
    fun testDifferentConfigs() = clientTests(listOf("OkHttp")) {
        config {
            install(SSE) {
                showCommentEvents()
            }
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50", showCommentEvents = false) {
                var size = 0
                incoming.collectIndexed { i, it ->
                    assertEquals("${2 * i + 1}", it.data)
                    size++
                }
                assertEquals(50, size)
            }

            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    if (i % 2 == 0) {
                        assertEquals("$i", it.comments)
                    } else {
                        assertEquals("$i", it.data)
                    }
                    size++
                }
                assertEquals(100, size)
            }
        }
    }

    @Test
    fun testRequestTimeoutIsNotApplied() = clientTests {
        config {
            install(SSE)

            install(HttpTimeout) {
                requestTimeoutMillis = 10
            }
        }

        test { client ->
            client.sse("$TEST_SERVER/sse/hello?delay=20") {
                val result = incoming.single()
                assertEquals("hello 0", result.event)
            }
        }
    }

    @Test
    fun testWithAuthPlugin() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }

            install(SSE)
        }

        test { client ->
            client.sse("$TEST_SERVER/sse/auth") {
                val result = incoming.single()
                assertEquals("hello after refresh", result.data)
            }
        }
    }

    @Test
    fun testSseExceptionOn404Response() = clientTests(listOf("CIO", "Apache", "Apache5", "Android", "Java")) {
        config {
            install(SSE)
        }

        test { client ->
            kotlin.test.assertFailsWith<SSEException> {
                client.sse("$TEST_SERVER/sse/404") {}
            }.let {
                kotlin.test.assertContains(it.message!!, "Expected status code 200 but was: 404")
            }
        }
    }

    @Test
    fun testContentTypeWithCharset() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            val session = client.serverSentEventsSession("$TEST_SERVER/sse/content_type_with_charset")
            session.incoming.single().apply {
                assertEquals("0", id)
                assertEquals("hello 0", event)
                val lines = data?.lines() ?: emptyList()
                assertEquals(2, lines.size)
                assertEquals("hello", lines[0])
                assertEquals("from server", lines[1])
            }
            session.cancel()
        }
    }
}
