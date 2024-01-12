/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.callid

import io.ktor.callid.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.coroutines.*
import kotlin.test.*
import io.ktor.server.plugins.callid.CallId as ServerCallId

class CallIdTest {

    @Test
    fun testCallIdChainingFromCoroutineContext() = testApplication {
        val client = createClient {
            install(CallId)
        }

        install(ServerCallId) {
            generate { "call-id-1" }
        }
        routing {
            get("/1") {
                call.respond(client.get("2").bodyAsText())
            }
            get("2") {
                respondWithCallId()
            }
        }

        val response = client.get("/1").bodyAsText()
        assertEquals(response, "call-id-1:call-id-1:call-id-1")
    }

    @Test
    fun testCallIdAddGenerator() = testApplication {
        var counter = 0
        val client = createClient {
            install(CallId) {
                generate { "call-id-client-${counter++}" }
            }
        }

        install(ServerCallId) {
            generate { "call-id-server" }
            header(HttpHeaders.XRequestId)
        }
        routing {
            get("/1") {
                call.respond(client.get("2").bodyAsText())
            }
            get("2") {
                respondWithCallId()
            }
        }

        val response = client.get("/1").bodyAsText()
        assertEquals(response, "call-id-client-0:call-id-client-0:call-id-client-0")
    }

    @Test
    fun testRemoveCallIdFromCoroutineContext() = testApplication {
        var counter = 0
        val client = createClient {
            install(CallId) {
                useCoroutineContext = false
                generate { "call-id-client-${counter++}" }
            }
        }

        install(ServerCallId) {
            generate { "call-id-server" }
            header(HttpHeaders.XRequestId)
        }
        routing {
            get("/1") {
                call.respond(client.get("2").bodyAsText())
            }
            get("2") {
                respondWithCallId()
            }
        }

        val response = client.get("/1").bodyAsText()
        assertEquals(response, "call-id-client-1:call-id-client-1:call-id-client-1")
    }

    @Test
    fun testSetCustomInterceptor() = testApplication {
        val client = createClient {
            install(CallId) {
                intercept { request, callId -> request.parameter("callId", callId) }
            }
        }

        install(ServerCallId) {
            generate { "call-id-1" }
        }
        routing {
            get("/1") {
                call.respond(client.get("2").bodyAsText())
            }
            get("2") {
                val callIdFromHeader = call.request.headers[HttpHeaders.XRequestId]
                val callIdFromParameter = call.request.queryParameters["callId"]
                call.respond("$callIdFromHeader:$callIdFromParameter")
            }
        }

        val response = client.get("/1").bodyAsText()
        assertEquals(response, "null:call-id-1")
    }

    private suspend fun RoutingContext.respondWithCallId() {
        val callIdFromCall = call.callId ?: error("No call id in call")
        val callIdFromContext = coroutineContext[KtorCallIdContextElement]?.callId ?: error("No call id in context")
        val callIdFromHeader = call.request.headers[HttpHeaders.XRequestId] ?: error("No call id in header")
        call.respond("$callIdFromCall:$callIdFromContext:$callIdFromHeader")
    }
}
