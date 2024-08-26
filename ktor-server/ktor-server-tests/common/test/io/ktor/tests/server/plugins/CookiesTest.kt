/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*

class CookiesTest {
    @Test
    fun simple_cookies() = withRawCookies("SESSION=123; HOST=abc") {
        assertEquals("123", cookies["SESSION"])
        assertEquals("abc", cookies["HOST"])
    }

    @Test
    fun old_fashioned_obsolete_cookies_syntax() = withRawCookies("\$Version=1; SESSION=456; \$Path=/; HOST=def") {
        assertEquals("456", cookies["SESSION"])
        assertEquals("def", cookies["HOST"])
    }

    @Test
    fun missing_space() = withRawCookies("SESSION=456;\$Path=/; HOST=def") {
        assertEquals("456", cookies["SESSION"])
        assertEquals("def", cookies["HOST"])
    }

    @Test
    fun decode_encoded_cookies() = withRawCookies("SESSION=1+2") {
        assertEquals("1 2", cookies["SESSION"])
    }

    @Test
    fun decode_dquotes_encoded_cookies() = withRawCookies("SESSION=\"1 2\"") {
        assertEquals("1 2", cookies["SESSION", CookieEncoding.DQUOTES])
    }

    @Test
    fun add_cookies_simple() = testSetCookies("SESSION=123") {
        cookies.append("SESSION", "123")
    }

    @Test
    fun add_cookies_with_max_age() = testSetCookies("SESSION=123; Max-Age=7") {
        cookies.append("SESSION", "123", maxAge = 7L)
    }

    @Test
    fun add_cookies_with_flags_and_extensions() = testSetCookies("SESSION=123; Secure; Flag; Test=1") {
        cookies.append("SESSION", "123", secure = true, extensions = linkedMapOf("Flag" to null, "Test" to "1"))
    }

    @Suppress("DEPRECATION_ERROR")
    @Test
    fun add_cookies_expired() = testSetCookies("SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") {
        cookies.appendExpired("SESSION")
    }

    @Test
    fun add_cookies_bad_characters() = testSetCookies("AB=1-2") {
        cookies.append("AB", "1-2")
    }

    @Test
    fun add_cookies_encoding_simple_no_quotes() = testSetCookies("A=1") {
        cookies.append("A", "1", encoding = CookieEncoding.DQUOTES)
    }

    @Test
    fun add_cookies_encoding_simple_with_quotes() = testSetCookies("A=\"1 2\"") {
        cookies.append("A", "1 2", encoding = CookieEncoding.DQUOTES)
    }

    @Test
    fun add_cookie_and_get_it() = testApplicationResponse {
        cookies.append("key", "value")

        assertEquals("value", cookies["key"]?.value)
        assertNull(cookies["other"])
    }

    @Test
    fun add_multiple_cookies() = testApplicationResponse {
        cookies.append("a", "1")
        cookies.append("b", "2")

        assertEquals(listOf("a=1", "b=2"), headers.values("Set-Cookie").map { it.cutSetCookieHeader() })
    }

    /*
     * We do not enforce that Secure flag matches protocol, because we could be behind a proxy.  There are other ways
     * to ensure that secure cookies are not vulnerable to man-in-the-middle attacks, but we leave this to the
     * developer.
     */
    @Test
    fun testSecureCookie() = testApplication {
        routing {
            get("/*") {
                call.response.cookies.append("S", "secret", secure = true)
                call.respond("ok")
            }
        }
        val response = client.get("/cookie-monster")
        assertEquals("S=secret; Secure", response.headers["Set-Cookie"]?.cutSetCookieHeader())
    }

    private fun testSetCookies(
        expectedHeaderContent: String,
        block: PipelineResponse.() -> Unit
    ) = testApplicationResponse {
        block()
        assertEquals(expectedHeaderContent, headers["Set-Cookie"]?.cutSetCookieHeader())
    }

    private fun testApplicationResponse(
        block: TestApplicationResponse.() -> Unit
    ) = testApplication {
        application {
            val call = TestApplicationCall(this, coroutineContext = Dispatchers.Default)
            call.request.protocol = "https"
            call.response.apply(block)
        }
    }

    private fun withRawCookies(
        header: String,
        block: TestApplicationRequest.() -> Unit
    ) = testApplication {
        application {
            val call = TestApplicationCall(this, coroutineContext = Dispatchers.Default)
            call.request.protocol = "https"
            call.request.addHeader("Cookie", header)
            call.request.apply(block)
        }
    }

    private fun String.cutSetCookieHeader() = substringBeforeLast("\$x-enc").trimEnd().removeSuffix(";")
}
