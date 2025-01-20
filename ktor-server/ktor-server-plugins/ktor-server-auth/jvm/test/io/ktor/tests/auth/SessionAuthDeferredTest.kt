/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sessions.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.*

class SessionAuthDeferredTest : SessionAuthTest() {

    @BeforeTest
    fun setProperty() {
        System.setProperty("io.ktor.server.sessions.deferred", "true")
    }

    @AfterTest
    fun clearProperty() {
        System.clearProperty("io.ktor.server.sessions.deferred")
    }

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
