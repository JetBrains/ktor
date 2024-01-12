/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CookiesTest : ClientLoader() {
    private val hostname = "http://127.0.0.1/cookies"
    private val TEST_HOST = "$TEST_SERVER/cookies"
    private val domain = "127.0.0.1"

    @Test
    fun testAccept() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get(TEST_HOST).body<Unit>()
            client.cookies(hostname).let {
                assertEquals(1, it.size)
                assertEquals("my-awesome-value", it["hello-cookie"]!!.value)
            }
        }
    }

    @Test
    fun testUpdate() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("id", "1", domain = domain))
                }
            }
        }

        test { client ->
            repeat(10) {
                val before = client.getId()
                client.get("$TEST_HOST/update-user-id").body<Unit>()
                assertEquals(before + 1, client.getId())
                assertEquals("ktor", client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testExpiration() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("id", "777", domain = domain, path = "/"))
                }
            }

            test { client ->
                assertFalse(client.cookies(hostname).isEmpty())
                client.get("$TEST_HOST/expire").body<Unit>()
                assertTrue(client.cookies(hostname).isEmpty(), "cookie should be expired")
            }
        }
    }

    @Test
    fun testConstant() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies) {
                storage = ConstantCookiesStorage(Cookie("id", "1", domain = domain))
            }
        }

        test { client ->
            repeat(3) {
                client.get("$TEST_HOST/update-user-id").body<Unit>()
                assertEquals(1, client.getId())
                assertNull(client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testMultipleCookies() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("first", "first-cookie", domain = domain))
                    addCookie(hostname, Cookie("second", "second-cookie", domain = domain))
                }
            }
        }

        test { client ->
            val response = client.get("$TEST_HOST/multiple").body<String>()
            assertEquals("Multiple done", response)
        }
    }

    @Test
    fun testPath() = clientTests(listOf("js")) {
        config {
            install(HttpCookies)
        }

        test { client ->
            assertEquals("OK", client.get("$TEST_HOST/withPath").body())
            assertEquals("OK", client.get("$TEST_HOST/withPath/something").body())
        }
    }

    @Test
    fun testWithLeadingDot() = clientTests(listOf("Js", "Darwin", "DarwinLegacy", "native:CIO")) {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get("https://m.vk.com").body<Unit>()
            assertTrue(client.cookies("https://.vk.com").isNotEmpty())
            assertTrue(client.cookies("https://vk.com").isNotEmpty())
            assertTrue(client.cookies("https://m.vk.com").isNotEmpty())
            assertTrue(client.cookies("https://m.vk.com").isNotEmpty())

            assertTrue(client.cookies("https://google.com").isEmpty())
        }
    }

    @Test
    fun caseSensitive() = clientTests(listOf("Js", "Darwin", "DarwinLegacy")) {
        config {
            install(HttpCookies)
        }

        test { client ->
            try {
                client.get("$TEST_HOST/foo").body<Unit>()
                client.get("$TEST_HOST/FOO").body<Unit>()
            } catch (cause: Throwable) {
                throw cause
            }
        }
    }

    @Test
    fun testMultipleCookiesWithComma() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("fir,st", "first, cookie", domain = domain))
                    addCookie(hostname, Cookie("sec,ond", "second, cookie", domain = domain))
                }
            }
        }

        test { client ->
            val response = client.get("$TEST_HOST/multiple-comma").body<String>()
            val cookies = client.cookies(hostname)
            assertEquals("first, cookie", cookies["fir,st"]!!.value)
            assertEquals("second, cookie", cookies["sec,ond"]!!.value)
            assertEquals("third cookie", cookies["third"]!!.value)
            assertEquals("fourth cookie", cookies["fourth"]!!.value)

            assertEquals("Multiple done", response)
        }
    }

    @Test
    fun testCookiesEncodedWithRespectiveEncoding() = clientTests(listOf("Js")) {
        val cookies = listOf(
            Cookie("uri", "first, cookie", domain = domain, encoding = CookieEncoding.URI_ENCODING),
            Cookie("raw", "first%2C+cookie", domain = domain, encoding = CookieEncoding.RAW),
            Cookie("base64", "first, cookie", domain = domain, encoding = CookieEncoding.BASE64_ENCODING),
            Cookie("dquotes", "first, cookie", domain = domain, encoding = CookieEncoding.DQUOTES)
        )
        config {
            install(HttpCookies) {
                default {
                    cookies.forEach { addCookie(hostname, it) }
                }
            }
        }

        test { client ->
            client.prepareGet("$TEST_HOST/encoded").execute { httpResponse ->
                val response = httpResponse.bodyAsText()
                val cookieStrings = response.split("; ").filter { it.isNotBlank() }
                assertEquals(4, cookieStrings.size)
                assertEquals("uri=first%2C+cookie", cookieStrings[0])
                assertEquals("raw=first%2C+cookie", cookieStrings[1])
                assertEquals("base64=Zmlyc3QsIGNvb2tpZQ==", cookieStrings[2])
                assertEquals("dquotes=\"first, cookie\"", cookieStrings[3])
            }
        }
    }

    @Test
    fun testCookiesWithWrongValue() = clientTests(listOf("js", "Darwin", "DarwinLegacy", "WinHttp")) {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get("$TCP_SERVER/wrong-value").body<Unit>()
            val cookies = client.plugin(HttpCookies).get(Url(TCP_SERVER))
            val expected = Cookie(
                "___utmvazauvysSB",
                "kDu\u0001xSkE",
                CookieEncoding.RAW,
                maxAge = 900,
                domain = "127.0.0.1",
                path = "/"
            )

            assertEquals(1, cookies.size)
            assertEquals(expected, cookies.first())
        }
    }

    @Test
    fun testRequestBuilderSingleCookie() = clientTests(listOf("Js")) {
        test { client ->
            val result = client.get("$TEST_HOST/respond-single-cookie") {
                cookie("single", value = "abacaba")
            }.body<String>()
            assertEquals("abacaba", result)
        }
    }

    @Test
    fun testRequestBuilderMultipleCookies() = clientTests(listOf("Js")) {
        test { client ->
            val result = client.get("$TEST_HOST/respond-a-minus-b") {
                cookie("a", value = "10")
                cookie("b", value = "4")
            }.body<String>()
            assertEquals("6", result)
        }
    }

    @Test
    fun testSeparatedBySemicolon() = clientTests(listOf("Js")) {
        test { client ->
            client.get("$TEST_HOST/encoded") {
                cookie("firstCookie", "first")
                header("Cookie", "secondCookie=second")
            }.bodyAsText().also {
                assertEquals("firstCookie=first; secondCookie=second", it)
            }
        }
    }

    private suspend fun HttpClient.getId() = cookies(hostname)["id"]!!.value.toInt()
}
