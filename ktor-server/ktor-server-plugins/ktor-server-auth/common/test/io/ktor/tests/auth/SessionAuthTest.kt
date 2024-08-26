/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlin.test.*

class SessionAuthTest {
    @Test
    fun testSessionOnly() = testApplication {
        install(Sessions) {
            cookie<MySession>("S")
        }
        install(Authentication) {
            session<MySession> {
                validate { it }
                challenge {
                    call.respond(UnauthorizedResponse())
                }
            }
        }

        routing {
            authenticate {
                get("/") { call.respondText("Secret info") }
                get("/logout") {
                    call.sessions.clear<MySession>()
                    call.respondRedirect("/")
                }
                get("/child/logout") {
                    call.sessions.clear<MySession>()
                    call.respondRedirect("/")
                }
            }
        }

        client.get("/").let { call ->
            assertEquals(HttpStatusCode.Unauthorized, call.status)
        }

        client.get("/") {
            header("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }

        val cookieStorage = AcceptAllCookiesStorage()

        client.config {
            expectSuccess = false
            install(HttpCookies) {
                storage = cookieStorage
            }
        }.use { client ->
            cookieStorage.addCookie(
                "/",
                Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
            )

            val first = client.get("/child/logout")
            first.body<String>()
            assertEquals(HttpStatusCode.Unauthorized, first.status)

            cookieStorage.addCookie(
                "/",
                Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
            )

            val second = client.get("logout")
            second.body<String>()
            assertEquals(HttpStatusCode.Unauthorized, second.status)
        }
    }

    @Test
    fun testSessionAndForm() = testApplication {
        install(Sessions) {
            cookie<MySession>("S")
        }
        install(Authentication) {
            session<MySession> {
                challenge {}
                validate { session -> session }
            }
            form("f") {
                challenge("/login")
                validate { null }
            }
        }

        routing {
            authenticate {
                authenticate("f") {
                    get("/") { call.respondText("Secret info") }
                }
            }
        }

        createClient { followRedirects = false }.get("/").let { call ->
            assertEquals(HttpStatusCode.Found, call.status)
        }

        client.get("/") {
            header("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testSessionWithEmptyValidateRespondsWith401() = testApplication {
        application {
            install(Sessions) {
                cookie<MySession>("cookie")
            }
            install(Authentication) {
                session<MySession>("auth-session")
            }

            routing {
                authenticate("auth-session") {
                    get("/") {
                        call.respondText { "OK" }
                    }
                }
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Serializable
    data class MySession(val id: Int)
}
