/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.methodoverride.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class XHttpMethodOverrideTest {
    @Test
    fun testNoFeature() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Get, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }
        }

        assertEquals(
            "GET",
            client.get("/") {
                header(HttpHeaders.XHttpMethodOverride, "DELETE")
            }.bodyAsText()
        )
    }

    @Test
    fun testWithFeatureMethodDelete() = testApplication {
        install(XHttpMethodOverride)

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Delete, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }
        }

        assertEquals(
            "DELETE",
            client.get("/") {
                header(HttpHeaders.XHttpMethodOverride, "DELETE")
            }.bodyAsText()
        )
    }

    @Test
    fun testWithFeatureMethodDeleteRouting() = testApplication {
        install(XHttpMethodOverride)

        routing {
            delete("/") {
                call.respondText("1")
            }
            get("/") {
                call.respondText("2")
            }
        }

        assertEquals(
            "1",
            client.get("/") {
                header(HttpHeaders.XHttpMethodOverride, "DELETE")
            }.bodyAsText()
        )

        assertEquals(
            "2",
            client.get("/").bodyAsText()
        )
    }

    @Test
    fun testWithFeatureMethodPatch() = testApplication {
        install(XHttpMethodOverride)

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Patch, call.request.origin.method)
                assertEquals("PATCH", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }
        }

        assertEquals(
            "PATCH",
            client.post("/") {
                header(HttpHeaders.XHttpMethodOverride, "PATCH")
            }.bodyAsText()
        )
    }

    @Test
    fun testWithFeatureCustomHeaderName() = testApplication {
        install(XHttpMethodOverride) {
            headerName = "X-My-Header"
        }

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Get, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }
        }

        assertEquals(
            "GET",
            client.get("/") {
                header(HttpHeaders.XHttpMethodOverride, "DELETE")
                header("X-My-Header", "GET")
            }.bodyAsText()
        )
    }

    @Test
    fun testMethodOverrideWithNoExistingMethod() = testApplication {
        install(XHttpMethodOverride)

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.head("/") {
            header(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testMethodOverrideWithForwardedFor() = testApplication {
        install(XHttpMethodOverride)

        install(XForwardedHeaders)

        routing {
            get("/") {
                @Suppress("DEPRECATION_ERROR")
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.head("/") {
            header(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
            header(HttpHeaders.XForwardedFor, "client, proxy")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testMethodOverrideWithForwardedPort() = testApplication {
        install(XHttpMethodOverride)

        install(XForwardedHeaders)

        routing {
            get("/") {
                @Suppress("DEPRECATION_ERROR")
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(90, port)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.head("/") {
            header(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
            header(HttpHeaders.XForwardedHost, "host:90")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testMethodOverrideWithForwardedNoPort() = testApplication {
        install(XHttpMethodOverride)

        install(XForwardedHeaders)

        routing {
            get("/") {
                @Suppress("DEPRECATION_ERROR")
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.head("/") {
            header(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
            header(HttpHeaders.XForwardedHost, "host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testMethodOverrideWithForwarded() = testApplication {
        install(XHttpMethodOverride)

        install(ForwardedHeaders)

        routing {
            get("/") {
                @Suppress("DEPRECATION_ERROR")
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(443, port)
                    assertEquals("client", remoteHost)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.head("/") {
            header(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
            header(HttpHeaders.Forwarded, "for=client;proto=https;host=host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }
}
