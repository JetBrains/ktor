/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class HSTSTest {
    @Test
    fun testHttp() = testApplication {
        application {
            testApp()
        }

        assertNull(client.get("/").headers[HttpHeaders.StrictTransportSecurity])
    }

    @Test
    fun testHttps() = testApplication {
        application {
            testApp()
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "some")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        application {
            install(XForwardedHeaders)
            routing {
                route("/1") {
                    install(HSTS) {
                        maxAgeInSeconds = 10
                        includeSubDomains = true
                        preload = true
                        customDirectives["some"] = "va=lue"
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

        client.get("/1") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "some")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }

        client.get("/2").let {
            assertNull(it.headers[HttpHeaders.StrictTransportSecurity])
        }
    }

    @Test
    fun testCustomPort() = testApplication {
        application {
            testApp()
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "some:8443")
        }.let {
            assertNull(it.headers[HttpHeaders.StrictTransportSecurity])
        }
    }

    @Test
    fun testSetCustomPort() = testApplication {
        application {
            testApp {
                filter { call ->
                    call.request.origin.run { scheme == "https" && serverPort == 8443 }
                }
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "some:8443")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
    }

    @Test
    fun testHttpsCustomDirectiveNoValue() = testApplication {
        application {
            testApp {
                customDirectives.clear()
                customDirectives["some"] = null
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload; some",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
    }

    @Test
    fun testHttpsNoCustomDirectives() = testApplication {
        application {
            testApp {
                customDirectives.clear()
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
    }

    @Test
    fun testHttpsMaxAgeOnly() = testApplication {
        application {
            testApp {
                customDirectives.clear()
                includeSubDomains = false
                preload = false
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let {
            assertEquals("max-age=10", it.headers[HttpHeaders.StrictTransportSecurity])
        }
    }

    @Test
    fun testHttpsHostOverride() = testApplication {
        application {
            testApp {
                customDirectives.clear()
                includeSubDomains = true

                withHost("differing") {
                    maxAgeInSeconds = 10
                    preload = true
                    includeSubDomains = false
                }
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let {
            assertEquals(
                "max-age=10; includeSubDomains; preload",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "differing")
        }.let {
            assertEquals(
                "max-age=10; preload",
                it.headers[HttpHeaders.StrictTransportSecurity]
            )
        }
    }

    private fun Application.testApp(block: HSTSConfig.() -> Unit = {}) {
        install(XForwardedHeaders)
        install(HSTS) {
            maxAgeInSeconds = 10
            includeSubDomains = true
            preload = true
            customDirectives["some"] = "va=lue"

            block()
        }

        routing {
            get("/") {
                call.respond("ok")
            }
        }
    }
}
