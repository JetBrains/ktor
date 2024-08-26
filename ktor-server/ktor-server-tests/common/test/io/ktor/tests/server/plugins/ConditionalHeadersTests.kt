// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ETagsTest {
    private fun withConditionalApplication(body: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ConditionalHeaders) {
                version { _, _ -> listOf(EntityTagVersion("tag1")) }
            }
            routing {
                handle {
                    call.respondText("response")
                }
            }
        }
        body()
    }

    @Test
    fun testNoConditions() = withConditionalApplication {
        val result = client.get {}
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchConditionAccepted() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfMatch, "tag1")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testNoDoubleHeader() = testApplication {
        install(ConditionalHeaders) {
            version { _, _ -> listOf(EntityTagVersion("tag1")) }
        }
        routing {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "tag2")
                call.respondText("response")
            }
        }

        val result = client.get {}
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals(listOf("tag2"), result.headers.getAll(HttpHeaders.ETag))
    }

    @Test
    fun testIfMatchConditionFailed() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfMatch, "tag2")
        }
        assertEquals(HttpStatusCode.PreconditionFailed, result.status)
    }

    @Test
    fun testIfNoneMatchConditionAccepted() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "tag1")
        }
        assertEquals(HttpStatusCode.NotModified, result.status)
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfNoneMatchWeakConditionAccepted() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "W/tag1")
        }
        assertEquals(HttpStatusCode.NotModified, result.status)
    }

    @Test
    fun testIfNoneMatchConditionFailed() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchStar() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfMatch, "*")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfNoneMatchStar() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "*")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        // note: star for if-none-match is a special case
        // that should be handled separately
        // so we always pass it
    }

    @Test
    fun testIfNoneMatchListConditionFailed() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "tag0,tag1,tag3")
        }
        assertEquals(HttpStatusCode.NotModified, result.status)
    }

    @Test
    fun testIfNoneMatchListConditionSuccess() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionAccepted() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfMatch, "tag0,tag1,tag3")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals("response", result.bodyAsText())
        assertEquals("\"tag1\"", result.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionFailed() = withConditionalApplication {
        val result = client.get {
            header(HttpHeaders.IfMatch, "tag0,tag2,tag3")
        }
        assertEquals(HttpStatusCode.PreconditionFailed, result.status)
    }
}
