/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.test.*

/**
 * This test class demonstrates how request cookies behave in a client
 * when expired and redirected by server (session log out);
 * and how [HttpCookies] feature manages request cookies in this context.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.tests.CookiesAndRedirectMockedTest)
 */
class CookiesAndRedirectMockedTest {
    @Test
    fun testRequestCookieIsSentWithoutCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                assertEquals("test=value", request.headers[HttpHeaders.Cookie])
                respondOk()
            }
        }

        test { client ->
            client.get("/example") {
                cookie("test", "value")
            }
        }
    }

    /**
     * This test shows that without HttpCookies feature installed,
     * the client will ignore (session) cookie expiration from server in a log-out-and-redirect scenario.
     * This kinda makes sense due to [HttpRequestBuilder.cookie] being a fairly simple and direct way to add cookie to request;
     * but this result behaviour may or may not be desirable.
     */
    @Test
    fun testRequestCookieCannotBeExpiredAndIsSentWhenRedirectedWithoutCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/logout" -> {
                        val expiredCookie = Cookie("SID", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertEquals("SID=123", request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            followRedirects = true
        }

        test { client ->
            client.get("/example/logout") {
                cookie("SID", "123")
            }.let { assertEquals("redirected", it.bodyAsText()) }
        }
    }

    /**
     * [HttpCookies] feature should respect (additional) request cookies
     */
    @Test
    fun testRequestCookieIsSentWithCookiesFeature() = testWithEngine(MockEngine) {
        val storage = AcceptAllCookiesStorage()
        config {
            server { request ->
                val cookies = request.headers[HttpHeaders.Cookie]!!.split("; ")
                assertContains(cookies, "test=value")
                assertContains(cookies, "other=abc")
                respondOk()
            }
            install(HttpCookies) {
                this.storage = storage
            }
        }

        test { client ->
            storage.addCookie("/example", Cookie("other", "abc"))
            client.get("/example") {
                cookie("test", "value")
            }
        }
    }

    /**
     * [HttpCookies] feature should manage request cookies and its expiration properly,
     * and not send it in redirection if it's expired by server.
     */
    @Test
    fun testRequestCookieCanBeExpiredAndIsNotSentWhenRedirectedWithCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/logout" -> {
                        val expiredCookie = Cookie("SID", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertNull(request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            install(HttpCookies)
            followRedirects = true
        }

        test { client ->
            client.get("/example/logout") {
                cookie("SID", "123")
            }.let { assertEquals("redirected", it.bodyAsText()) }
        }
    }

    @Test
    fun testStorageCookieCanBeExpiredAndIsNotSentWhenRedirectedWithCookiesFeature() = testWithEngine(MockEngine) {
        val storage = AcceptAllCookiesStorage()
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/logout" -> {
                        val expiredCookie = Cookie("SID", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertNull(request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            install(HttpCookies) {
                this.storage = storage
            }
            followRedirects = true
        }

        test { client ->
            storage.addCookie("/example", Cookie("SID", "123"))
            assertEquals("redirected", client.get("/example/logout").bodyAsText())
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.server(handler: MockRequestHandler) {
        engine {
            addHandler(handler)
        }
    }

    private fun MockRequestHandleScope.respondRedirectWithCookie(location: String, cookie: Cookie): HttpResponseData =
        respond(
            "redirect",
            HttpStatusCode.TemporaryRedirect,
            headers = Headers.build {
                append(HttpHeaders.Location, location)
                append(HttpHeaders.SetCookie, renderSetCookieHeader(cookie))
            }
        )
}
