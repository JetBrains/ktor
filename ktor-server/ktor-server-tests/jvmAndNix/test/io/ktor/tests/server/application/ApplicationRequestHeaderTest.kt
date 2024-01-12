/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class ApplicationRequestHeaderTest {

    @Test
    fun `an application that handles requests to foo`() = withTestApplication {
        on("making an unauthenticated request to /foo") {
            application.routing {
                get("/foo") {
                    it("should map uri to /foo") {
                        assertEquals("/foo", call.request.uri)
                    }
                    it("should map authorization to empty string") {
                        assertEquals("", call.request.authorization())
                    }
                    it("should return empty string as queryString") {
                        assertEquals("", call.request.queryString())
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }

            val status = handleRequest {
                uri = "/foo"
                method = HttpMethod.Get
                addHeader(HttpHeaders.Authorization, "")
            }.response.status()

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun `an application that handles requests to foo with parameters`() = withTestApplication {
        on("making a request to /foo?key1=value1&key2=value2") {
            application.routing {
                get("/foo") {
                    it("should map uri to /foo?key1=value1&key2=value2") {
                        assertEquals("/foo?key1=value1&key2=value2", call.request.uri)
                    }
                    it("should map two parameters key1=value1 and key2=value2") {
                        val params = call.request.queryParameters
                        assertEquals("value1", params["key1"])
                        assertEquals("value2", params["key2"])
                    }
                    it("should map queryString to key1=value1&key2=value2") {
                        assertEquals("key1=value1&key2=value2", call.request.queryString())
                    }
                    it("should map document to foo") {
                        assertEquals("foo", call.request.document())
                    }
                    it("should map path to /foo") {
                        assertEquals("/foo", call.request.path())
                    }
                    it("should map host to host.name.com") {
                        assertEquals("host.name.com", call.request.host())
                    }
                    it("should map port to 8888") {
                        assertEquals(8888, call.request.port())
                    }

                    call.respond(HttpStatusCode.OK)
                }
                get("/default-port") {
                    it("should map port to 80") {
                        assertEquals(80, call.request.port())
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }

            val status = handleRequest {
                uri = "/foo?key1=value1&key2=value2"
                method = HttpMethod.Get
                addHeader(HttpHeaders.Host, "host.name.com:8888")
            }.response.status()

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }

            val status2 = handleRequest {
                uri = "/default-port"
                method = HttpMethod.Get
                addHeader(HttpHeaders.Host, "host.name.com")
            }.response.status()

            it("should handle second request") {
                assertEquals(HttpStatusCode.OK, status2)
            }
        }
    }

    @Test
    fun `an application that handles requests to root with parameters`() = withTestApplication {
        on("making a request to /?key1=value1&key2=value2") {
            application.routing {
                get("/") {
                    it("should map uri to /?key1=value1&key2=value2") {
                        assertEquals("/?key1=value1&key2=value2", call.request.uri)
                    }
                    it("should map two parameters key1=value1 and key2=value2") {
                        val params = call.request.queryParameters
                        assertEquals("value1", params["key1"])
                        assertEquals("value2", params["key2"])
                    }
                    it("should map queryString to key1=value1&key2=value2") {
                        assertEquals("key1=value1&key2=value2", call.request.queryString())
                    }
                    it("should map document to empty") {
                        assertEquals("", call.request.document())
                    }
                    it("should map path to empty") {
                        assertEquals("/", call.request.path())
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }

            val status = handleRequest {
                uri = "/?key1=value1&key2=value2"
                method = HttpMethod.Get
            }.response.status()

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }
}
