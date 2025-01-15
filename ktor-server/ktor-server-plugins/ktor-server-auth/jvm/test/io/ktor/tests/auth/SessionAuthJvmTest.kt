/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.session
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.sessions.serialization.KotlinxSessionSerializer
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionAuthJvmTest {

    @Test
    fun sessionIgnoredForNonPublicEndpoints() = testApplication {
        val brokenStorage = object : SessionStorage {
            override suspend fun write(id: String, value: String) = Unit
            override suspend fun invalidate(id: String) = error("invalidate called")
            override suspend fun read(id: String): String = error("read called")
        }
        application {
            install(Sessions) {
                cookie<MySession>("S", storage = brokenStorage) {
                    serializer = KotlinxSessionSerializer(Json.Default)
                }
                deferred = true
            }
            install(Authentication.Companion) {
                session<MySession> {
                    validate { it }
                }
            }
            routing {
                authenticate {
                    get("/authenticated") {
                        call.respondText("Secret info")
                    }
                }
                post("/session") {
                    call.sessions.set(MySession(1))
                    call.respondText("OK")
                }
                get("/public") {
                    call.respondText("Public info")
                }
            }
        }
        val withCookie: HttpRequestBuilder.() -> Unit = {
            header("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
        }

        assertEquals(HttpStatusCode.Companion.OK, client.post("/session").status)
        assertEquals(HttpStatusCode.Companion.OK, client.get("/public", withCookie).status)
        assertFailsWith<IllegalStateException> {
            client.get("/authenticated", withCookie).status
        }
    }

    @Serializable
    data class MySession(val id: Int)
}
