/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class BearerAuthTest {

    @Test
    fun `unauthorized with no auth`() = testApplication {
        configureServer()

        val response = client.get("/")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `successful with valid token`() = testApplication {
        configureServer()

        val response = client.get("/") {
            withToken("letmein")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("admin", response.bodyAsText())
    }

    @Test
    fun `successful with different cased scheme`() = testApplication {
        configureServer()

        val response = client.get("/") {
            header(HttpHeaders.Authorization, "${AuthScheme.Bearer.lowercase()} letmein")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("admin", response.bodyAsText())
    }

    @Test
    fun `successful with additional scheme`() = testApplication {
        install(Authentication) {
            bearer {
                authSchemes(additionalSchemes = arrayOf("Custom"))
                authenticate { UserIdPrincipal("admin") }
            }
        }

        routing {
            authenticate {
                route("/") {
                    handle { call.respondText("hi") }
                }
            }
        }

        val response = client.get("/") {
            header(HttpHeaders.Authorization, "Custom letmein")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hi", response.bodyAsText())
    }

    @Test
    fun `unauthorized with wrong scheme`() = testApplication {
        configureServer()

        val response = client.get("/") {
            header(HttpHeaders.Authorization, "Custom letmein")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unauthorized with no token`() = testApplication {
        configureServer()

        val response = client.get("/")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unauthorized with wrong token`() = testApplication {
        configureServer()

        val response = client.get("/") {
            withToken("opensaysme")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unauthorized with parameterized token`() = testApplication {
        configureServer()

        val response = client.get("/") {
            withToken("Token=letmein")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `exception when auth not configured`() = testApplication {
        configureServer(
            authenticate = { throw NotImplementedError() }
        )

        assertFailsWith<NotImplementedError> {
            client.get("/") {
                withToken("letmein")
            }
        }
    }

    @Test
    fun `unauthorized with custom realm and scheme`() = testApplication {
        configureServer(
            realm = "serverland",
            defaultScheme = "Stuff"
        )

        val response = client.get("/") {
            withToken("Token=letmein")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Stuff realm=serverland", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("", response.bodyAsText())
    }

    private fun HttpRequestBuilder.withToken(token: String) {
        header(HttpHeaders.Authorization, "${AuthScheme.Bearer} $token")
    }

    private fun ApplicationTestBuilder.configureServer(
        authenticate: AuthenticationFunction<BearerTokenCredential> = { token ->
            if (token.token == "letmein") UserIdPrincipal("admin") else null
        },
        realm: String? = null,
        defaultScheme: String = AuthScheme.Bearer
    ) {
        install(Authentication) {
            bearer {
                this.defaultScheme = defaultScheme
                this.realm = realm
                this.authenticate = authenticate
            }
        }

        routing {
            authenticate {
                route("/") {
                    handle { call.respondText(call.principal<UserIdPrincipal>()?.name ?: "") }
                }
            }
        }
    }
}
