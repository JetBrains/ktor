/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class CookiesTest {
    @Test
    fun `simple cookies`() {
        withRawCookies("SESSION=123; HOST=abc") {
            assertEquals("123", cookies["SESSION"])
            assertEquals("abc", cookies["HOST"])
        }
    }

    @Test
    fun `old fashioned obsolete cookies syntax`() {
        withRawCookies("\$Version=1; SESSION=456; \$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @Test
    fun `missing space`() {
        withRawCookies("SESSION=456;\$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @Test
    fun `decode encoded cookies`() {
        withRawCookies("SESSION=1+2") {
            assertEquals("1 2", cookies["SESSION"])
        }
    }

    @Test
    fun `decode dquotes encoded cookies`() {
        withRawCookies("SESSION=\"1 2\"") {
            assertEquals("1 2", cookies["SESSION", CookieEncoding.DQUOTES])
        }
    }

    @Test
    fun `add cookies simple`() {
        testSetCookies("SESSION=123") {
            cookies.append("SESSION", "123")
        }
    }

    @Test
    fun `add cookies with max age`() {
        testSetCookies("SESSION=123; Max-Age=7") {
            cookies.append("SESSION", "123", maxAge = 7L)
        }
    }

    @Test
    fun `add cookies with flags and extensions`() {
        testSetCookies("SESSION=123; Secure; Flag; Test=1") {
            cookies.append("SESSION", "123", secure = true, extensions = linkedMapOf("Flag" to null, "Test" to "1"))
        }
    }

    @Suppress("DEPRECATION_ERROR")
    @Test
    fun `add cookies expired`() {
        testSetCookies("SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") {
            cookies.appendExpired("SESSION")
        }
    }

    @Test
    fun `add cookies bad characters`() {
        testSetCookies("AB=1-2") {
            cookies.append("AB", "1-2")
        }
    }

    @Test
    fun `add cookies encoding simple no quotes`() {
        testSetCookies("A=1") {
            cookies.append("A", "1", encoding = CookieEncoding.DQUOTES)
        }
    }

    @Test
    fun `add cookies encoding simple with quotes`() {
        testSetCookies("A=\"1 2\"") {
            cookies.append("A", "1 2", encoding = CookieEncoding.DQUOTES)
        }
    }

    @Test
    fun `add cookie and get it`() {
        withTestApplicationResponse {
            cookies.append("key", "value")

            assertEquals("value", cookies["key"]?.value)
            assertNull(cookies["other"])
        }
    }

    @Test
    fun `add multiple cookies`() {
        withTestApplicationResponse {
            cookies.append("a", "1")
            cookies.append("b", "2")

            assertEquals(listOf("a=1", "b=2"), headers.values("Set-Cookie").map { it.cutSetCookieHeader() })
        }
    }

    @Test
    fun testSecureCookieHttp() {
        withTestApplication {
            application.routing {
                get("/*") {
                    call.response.cookies.append("S", "secret", secure = true)
                    call.respond("ok")
                }
            }

            assertFails {
                handleRequest(HttpMethod.Get, "/1")
            }
        }
    }

    @Test
    fun testSecureCookieHttps() {
        withTestApplication {
            application.routing {
                get("/*") {
                    call.response.cookies.append("S", "secret", secure = true)
                    call.respond("ok")
                }
            }

            handleRequest(HttpMethod.Get, "/2") {
                protocol = "https"
            }
        }
    }

    private fun testSetCookies(expectedHeaderContent: String, block: PipelineResponse.() -> Unit) {
        withTestApplicationResponse {
            block()
            assertEquals(expectedHeaderContent, headers["Set-Cookie"]?.cutSetCookieHeader())
        }
    }

    private fun withTestApplicationResponse(block: TestApplicationResponse.() -> Unit) {
        withTestApplication {
            createCall { protocol = "https" }.response.apply(block)
        }
    }

    private fun withRawCookies(header: String, block: TestApplicationRequest.() -> Unit) {
        withTestApplication {
            createCall { protocol = "https" }.request.apply {
                addHeader("Cookie", header)
                block()
            }
        }
    }

    private fun String.cutSetCookieHeader() = substringBeforeLast("\$x-enc").trimEnd().removeSuffix(";")
}
