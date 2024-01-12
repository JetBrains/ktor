/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

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

@Suppress("DEPRECATION")
class XHttpMethodOverrideTest {
    @Test
    fun testNoFeature() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Get, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }

            assertEquals(
                "GET",
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
                }.response.content
            )
        }
    }

    @Test
    fun testWithFeatureMethodDelete() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Delete, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }

            assertEquals(
                "DELETE",
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
                }.response.content
            )
        }
    }

    @Test
    fun testWithFeatureMethodDeleteRouting() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.routing {
                delete("/") {
                    call.respondText("1")
                }
                get("/") {
                    call.respondText("2")
                }
            }

            assertEquals(
                "1",
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
                }.response.content
            )

            assertEquals(
                "2",
                handleRequest(HttpMethod.Get, "/").response.content
            )
        }
    }

    @Test
    fun testWithFeatureMethodPatch() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Patch, call.request.origin.method)
                assertEquals("PATCH", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }

            assertEquals(
                "PATCH",
                handleRequest(HttpMethod.Post, "/") {
                    addHeader(HttpHeaders.XHttpMethodOverride, "PATCH")
                }.response.content
            )
        }
    }

    @Test
    fun testWithFeatureCustomHeaderName() {
        withTestApplication {
            application.install(XHttpMethodOverride) {
                headerName = "X-My-Header"
            }

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(HttpMethod.Get, call.request.origin.method)
                assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

                call.respondText(call.request.origin.method.value)
            }

            assertEquals(
                "GET",
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
                    addHeader("X-My-Header", "GET")
                }.response.content
            )
        }
    }

    @Test
    fun testMethodOverrideWithNoExistingMethod() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Head, "/") {
                addHeader(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testMethodOverrideWithForwardedFor() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.install(XForwardedHeaders)

            application.routing {
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

            handleRequest(HttpMethod.Head, "/") {
                addHeader(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
                addHeader(HttpHeaders.XForwardedFor, "client, proxy")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testMethodOverrideWithForwardedPort() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.install(XForwardedHeaders)

            application.routing {
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

            handleRequest(HttpMethod.Head, "/") {
                addHeader(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
                addHeader(HttpHeaders.XForwardedHost, "host:90")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testMethodOverrideWithForwardedNoPort() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.install(XForwardedHeaders)

            application.routing {
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

            handleRequest(HttpMethod.Head, "/") {
                addHeader(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
                addHeader(HttpHeaders.XForwardedHost, "host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testMethodOverrideWithForwarded() {
        withTestApplication {
            application.install(XHttpMethodOverride)

            application.install(ForwardedHeaders)

            application.routing {
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

            handleRequest(HttpMethod.Head, "/") {
                addHeader(HttpHeaders.XHttpMethodOverride, HttpMethod.Get.value)
                addHeader(HttpHeaders.Forwarded, "for=client;proto=https;host=host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }
}
