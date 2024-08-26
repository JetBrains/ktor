/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.callid.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlin.coroutines.*
import kotlin.test.*

class CallIdTest {
    @Test
    fun missingPlugin() = testApplication {
        handle {
            call.respond(call.callId.toString())
        }

        client.get("/").let { call ->
            assertEquals("null", call.bodyAsText())
        }
    }

    @Test
    fun customRetriever() = testApplication {
        install(CallId) {
            retrieve { call ->
                call.request.uri
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        assertEquals("/call-id", client.get("/call-id").bodyAsText())
    }

    @Test
    fun headerRetriever() = testApplication {
        install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
        }
        handle {
            call.respond(call.callId.toString())
        }

        client.get("/") { header(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.bodyAsText())
        }

        client.get("/") { }.let { call ->
            assertEquals("null", call.bodyAsText())
        }
    }

    @Test
    fun headerRetrieverWithGenerator() = testApplication {
        install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate {
                "generated-id"
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        client.get("/") { header(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.bodyAsText())
        }

        client.get("/") { }.let { call ->
            assertEquals("generated-id", call.bodyAsText())
        }
    }

    @Test
    fun headerRetrieverWithTextGenerator() = testApplication {
        val dictionary = "abc"
        val length = 64

        install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate(length, dictionary)
        }
        handle {
            call.respond(call.callId.toString())
        }

        client.get("/") { header(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.bodyAsText())
        }

        client.get("/") { }.let { call ->
            val generatedId = call.bodyAsText()
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue { dictionary.all { it in generatedId } }
            assertTrue(message = "It should be no non-dictionary characters") {
                generatedId.toCharArray().none { it !in dictionary }
            }
        }
    }

    @Test
    fun headerRetrieverWithDefaultTextGenerator() = testApplication {
        val dictionary = CALL_ID_DEFAULT_DICTIONARY
        val length = 64

        install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate(length = length)
        }
        handle {
            call.respond(call.callId.toString())
        }

        client.get("/") {
            header(HttpHeaders.XRequestId, "test-id")
        }.let { call ->
            assertEquals("test-id", call.bodyAsText())
        }

        client.get("/") { }.let { call ->
            val generatedId = call.bodyAsText()
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue(message = "It should be no non-dictionary characters") {
                generatedId.toCharArray().none { it !in dictionary }
            }
        }
    }

    @Test
    fun replyToHeader() = testApplication {
        install(CallId) {
            header(HttpHeaders.XRequestId)
            generate { "generated-call-id" }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // call id is provided by client
        client.get("/") { header(HttpHeaders.XRequestId, "client-id") }.let { call ->
            assertEquals("client-id", call.bodyAsText())
            assertEquals("client-id", call.headers[HttpHeaders.XRequestId])
        }

        // call id is generated
        client.get("/").let { call ->
            assertEquals("generated-call-id", call.bodyAsText())
            assertEquals("generated-call-id", call.headers[HttpHeaders.XRequestId])
        }
    }

    @Test
    fun testDefaultVerifierForRetrieve() = testApplication {
        install(CallId) {
            header(HttpHeaders.XRequestId)
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        client.get("/") {
            header(HttpHeaders.XRequestId, CALL_ID_DEFAULT_DICTIONARY)
        }.let { call ->
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.bodyAsText())
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.headers[HttpHeaders.XRequestId])
        }

        // invalid call id
        client.get("/") {
            header(HttpHeaders.XRequestId, "\u1000")
        }.let { call ->
            assertEquals("null", call.bodyAsText())
        }
    }

    @Test
    fun testDefaultVerifierForGenerate() = testApplication {
        install(CallId) {
            generate { if (it.request.uri == "/valid") CALL_ID_DEFAULT_DICTIONARY else "\u1000" }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        client.get("/valid").let { call ->
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.bodyAsText())
        }

        // invalid call id
        client.get("/invalid").let { call ->
            assertEquals("null", call.bodyAsText())
        }
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        routing {
            route("1") {
                install(CallId) {
                    generate { "test-id" }
                }
                get {
                    call.respond(call.callId.toString())
                }
            }
            route("2") {
                install(CallId) {
                    generate { "2222" }
                }
                get {
                    call.respond(call.callId.toString())
                }
            }
            get("3") {
                call.respond(call.callId.toString())
            }
        }

        assertEquals("test-id", client.get("/1").bodyAsText())
        assertEquals("2222", client.get("/2").bodyAsText())
        assertEquals("null", client.get("/3").bodyAsText())
    }

    @Test
    fun testCoroutineContextElement() = testApplication {
        install(CallId) {
            generate { "test-id" }
        }
        routing {
            route("1") {
                get {
                    call.respond(coroutineContext[KtorCallIdContextElement]?.callId ?: "not found")
                }
            }
        }

        assertEquals("test-id", client.get("/1").bodyAsText())
    }

    @Test
    fun testCustomVerifier() = testApplication {
        install(CallId) {
            header(HttpHeaders.XRequestId)
            verify {
                if (it.length < 3) throw RejectedCallIdException(it)
                return@verify it.length > 4
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        client.get("/") { header(HttpHeaders.XRequestId, "12345") }.let { call ->
            assertEquals("12345", call.bodyAsText())
        }

        // invalid call id that should be ignored
        client.get("/") { header(HttpHeaders.XRequestId, "123") }.let { call ->
            assertEquals("null", call.bodyAsText())
        }

        // invalid call id that should be rejected with error
        client.get("/") { header(HttpHeaders.XRequestId, "1") }.let { call ->
            assertEquals(HttpStatusCode.BadRequest.value, call.status.value)
        }
    }

    private fun ApplicationTestBuilder.handle(block: PipelineInterceptor<Unit, PipelineCall>) {
        application {
            intercept(ApplicationCallPipeline.Call, block)
        }
    }
}
