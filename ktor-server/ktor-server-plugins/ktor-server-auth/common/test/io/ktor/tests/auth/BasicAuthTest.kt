/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class BasicAuthTest {
    @Test
    fun testBasicAuthNoAuth() = testApplication {
        configureServer()

        val call = client.get("/")

        assertEquals(HttpStatusCode.Unauthorized, call.status)
        assertEquals("", call.bodyAsText())

        assertWWWAuthenticateHeaderExist(call)
        assertEquals("Basic realm=ktor-test, charset=UTF-8", call.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun testCharsetNull() = testApplication {
        install(Authentication) {
            basic {
                realm = "ktor-test"
                charset = null
                validate { null }
            }
        }

        routing {
            authenticate {
                get("/") { call.respondText("Secret info") }
            }
        }

        val call = client.get("/")

        assertEquals(
            "Basic realm=ktor-test",
            call.headers[HttpHeaders.WWWAuthenticate]
        )
    }

    @Test
    fun testBasicAuthNoInterceptor() = testApplication {
        val user = "user1"
        val p = "user1"

        application {
            intercept(ApplicationCallPipeline.Plugins) {
                val authInfo = call.request.basicAuthenticationCredentials()
                assertNotNull(authInfo)
                assertEquals(authInfo, call.request.basicAuthenticationCredentials())

                assertEquals(user, authInfo.name)
                assertEquals(p, authInfo.password)

                call.response.status(HttpStatusCode.OK)
                call.respondText("ok")
            }
        }

        val call = handleRequestWithBasic("/", user, p)

        assertEquals(HttpStatusCode.OK, call.status)
        assertEquals("ok", call.bodyAsText())
    }

    @Test
    fun testBasicAuthSuccess() = testApplication {
        configureServer()
        val user = "user1"
        val p = "user1"

        val call = handleRequestWithBasic("/", user, p)

        assertEquals(HttpStatusCode.OK, call.status)
        assertEquals("Secret info", call.bodyAsText())
    }

    @Test
    fun testBadRequestOnInvalidHeader() = testApplication {
        configureServer()

        val call = client.get { header(HttpHeaders.Authorization, "B<sic code") }

        assertEquals(HttpStatusCode.BadRequest, call.status)
    }

    @Test
    fun testUtf8Charset() = testApplication {
        val user = "Лира"
        val p = "Лира"

        configureServer {
            if (it.name == user && it.password == p) UserIdPrincipal(it.name) else null
        }

        val call = handleRequestWithBasic("/", user, p, charset = Charsets.UTF_8)

        assertEquals(HttpStatusCode.OK, call.status)
        assertEquals("Secret info", call.bodyAsText())
    }

    @Test
    fun testBasicAuthFailed() = testApplication {
        configureServer()
        val user = "user1"
        val p = "wrong password"

        val call = handleRequestWithBasic("/", user, p)

        assertEquals(HttpStatusCode.Unauthorized, call.status)
        assertNotEquals("Secret info", call.bodyAsText())

        assertWWWAuthenticateHeaderExist(call)
    }

    @Test
    fun testBasicAuthDifferentScheme() = testApplication {
        configureServer()

        val call = client.get("/") {
            header(HttpHeaders.Authorization, "Bearer some-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, call.status)
        assertNotEquals("Secret info", call.bodyAsText())

        assertWWWAuthenticateHeaderExist(call)
    }

    @Test
    fun testBasicAuthInvalidBase64() = testApplication {
        configureServer()

        val call = client.get("/") {
            header(HttpHeaders.Authorization, "Basic +-test")
        }

        assertEquals(HttpStatusCode.Unauthorized, call.status)
        assertNotEquals("Secret info", call.bodyAsText())

        assertWWWAuthenticateHeaderExist(call)
    }

    @Test
    fun testBasicAuthFiltered() = testApplication {
        configureServer()
        val user = "user1"
        val p = "wrong password"

        val call = handleRequestWithBasic("/?backdoor", user, p)

        assertEquals(HttpStatusCode.OK, call.status)
        assertEquals("Secret info", call.bodyAsText())
    }

    @Test
    fun testSimplifiedFlow() = testApplication {
        install(Authentication) {
            basic {
                realm = "ktor-test"
                validate { c -> if (c.name == "good") UserIdPrincipal(c.name) else null }
            }
        }

        routing {
            authenticate {
                get("/") { call.respondText("Secret info") }
            }
        }

        handleRequestWithBasic("/", "bad", "").let { call ->
            assertEquals(HttpStatusCode.Unauthorized, call.status)
            assertNotEquals("Secret info", call.bodyAsText())

            assertWWWAuthenticateHeaderExist(call)
        }

        handleRequestWithBasic("/", "good", "").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Secret info", call.bodyAsText())
        }
    }

    @Test
    fun testNonConfiguredAuth() = testApplication {
        install(Authentication) {
            basic {}
        }

        routing {
            authenticate {
                get("/") {
                }
            }
        }

        assertFailsWith<NotImplementedError> {
            handleRequestWithBasic("/", "u", "a")
        }
    }

    private suspend fun ApplicationTestBuilder.handleRequestWithBasic(
        url: String,
        user: String,
        pass: String,
        charset: Charset = Charsets.ISO_8859_1
    ) = client.get(url) {
        val up = "$user:$pass"
        val encoded = up.toByteArray(charset).encodeBase64()
        header(HttpHeaders.Authorization, "Basic $encoded")
    }

    private fun assertWWWAuthenticateHeaderExist(call: HttpResponse) {
        assertNotNull(call.headers[HttpHeaders.WWWAuthenticate])
        val header = parseAuthorizationHeader(
            call.headers[HttpHeaders.WWWAuthenticate]!!
        ) as HttpAuthHeader.Parameterized

        assertEquals(AuthScheme.Basic, header.authScheme)
        assertEquals("ktor-test", header.parameter(HttpAuthHeader.Parameters.Realm))
    }

    private fun ApplicationTestBuilder.configureServer(
        validate: suspend (UserPasswordCredential) -> Any? = {
            if (it.name == it.password) UserIdPrincipal(it.name) else null
        }
    ) {
        install(Authentication) {
            basic {
                realm = "ktor-test"
                validate {
                    validate(it)
                }
                skipWhen { it.request.origin.uri.contains("backdoor") }
            }
        }

        routing {
            authenticate {
                route("/") {
                    handle { call.respondText("Secret info") }
                }
            }
        }
    }
}
