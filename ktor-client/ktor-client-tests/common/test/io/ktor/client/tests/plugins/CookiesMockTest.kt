/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.plugins

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CookiesMockTest {

    @Test
    fun testCompatibility() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    assertEquals("*/*", request.headers[HttpHeaders.Accept])
                    val rawCookies = request.headers[HttpHeaders.Cookie]!!
                    assertEquals(1, request.headers.getAll(HttpHeaders.Cookie)?.size!!)
                    assertEquals("first=\"1,2,3,4\"; second=abc", rawCookies)

                    respondOk()
                }
            }

            install(HttpCookies) {
                default {
                    addCookie("//localhost", Cookie("first", "1,2,3,4", encoding = CookieEncoding.DQUOTES))
                    addCookie("http://localhost", Cookie("second", "abc"))
                }
            }
        }

        test { client ->
            client.prepareGet { }.execute { }
        }
    }

    @Test
    fun testAllowedCharacters() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    assertEquals("myServer=value:value", request.headers[HttpHeaders.Cookie])
                    respondOk()
                }
            }

            install(HttpCookies) {
                default {
                    addCookie("http://localhost", Cookie("myServer", "value:value", encoding = CookieEncoding.RAW))
                }
            }
        }

        test { client ->
            client.get {}.body<String>()
        }
    }

    @Test
    fun testWithLeadingDotInDomain() = testWithEngine(MockEngine) {
        config {
            install(HttpCookies)

            engine {
                addHandler {
                    respond(
                        "OK",
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.SetCookie,
                            "myServer=value; Domain=.vk.com; secure"
                        )
                    )
                }
            }
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
}
