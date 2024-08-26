/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class CachingHeadersTest {
    @Test
    fun testNoPluginInstalled() = test(
        configure = {},
        test = { response ->
            assertEquals(null, response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun testByPass() = test(
        configure = {
            install(CachingHeaders)
        },
        test = { response ->
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun addNoStore() = test(
        configure = {
            install(CachingHeaders) {
                options { _, _ -> CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
            }
        },
        test = { response ->
            assertEquals("no-cache, private, no-store", response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun testAddMaxAgeAndNoStore() = test(
        configure = {
            install(CachingHeaders) {
                options { _, _ -> CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
                options { _, _ -> CachingOptions(CacheControl.MaxAge(15)) }
            }
        },
        test = { response ->
            assertEquals(
                "no-cache, no-store, max-age=15, private",
                response.headers[HttpHeaders.CacheControl]
            )
        }
    )

    object Immutable : CacheControl(null) {
        override fun toString(): String = "immutable"
    }

    @Test
    fun testCustomCacheControl() = test(
        configure = {
            install(CachingHeaders) {
                options { _, _ -> CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
                options { _, _ -> CachingOptions(Immutable) }
            }
        },
        test = { response ->
            assertEquals(
                "no-cache, private, no-store, immutable",
                response.headers[HttpHeaders.CacheControl]
            )
        }
    )

    @Test
    fun testSetInCall() = testApplication {
        install(CachingHeaders)
        routing {
            get("/") {
                call.caching = CachingOptions(CacheControl.NoCache(null))
                call.respondText("test")
            }
        }
        client.get("/").let { response ->
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun testSetInCallAndContent() = testApplication {
        install(CachingHeaders)
        routing {
            get("/") {
                call.caching = CachingOptions(CacheControl.NoCache(null))
                call.respondText("test") {
                    caching = CachingOptions(CacheControl.MaxAge(15))
                }
            }
        }
        client.get("/").let { response ->
            assertEquals("no-cache, max-age=15", response.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        application {
            routing {
                route("/1") {
                    install(CachingHeaders) {
                        options { _, _ -> CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
                        options { _, _ -> CachingOptions(CacheControl.MaxAge(15)) }
                    }
                    get {
                        call.respondText("test") {
                            caching = CachingOptions(CacheControl.NoCache(null))
                        }
                    }
                }
                get("/2") {
                    call.respondText("test") {
                        caching = CachingOptions(CacheControl.NoCache(null))
                    }
                }
            }
        }

        client.get("/1").let {
            assertEquals(
                "no-cache, no-store, max-age=15, private",
                it.headers[HttpHeaders.CacheControl]
            )
        }

        client.get("/2").let {
            assertNull(it.headers[HttpHeaders.CacheControl])
        }
    }

    private fun test(
        configure: Application.() -> Unit,
        test: (HttpResponse) -> Unit
    ) = testApplication {
        application {
            configure(this)

            routing {
                get("/") {
                    call.respondText("test") {
                        caching = CachingOptions(CacheControl.NoCache(null))
                    }
                }
            }
        }
        client.get("/").let {
            assertTrue(it.status.isSuccess())
            assertEquals("test", it.bodyAsText().trim())
            test(it)
        }
    }
}
