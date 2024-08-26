/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationRequestHeaderTest {

    @Test
    fun an_application_that_handles_requests_to_foo() = testApplication {
        on("making an unauthenticated request to /foo") {
            routing {
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

            val status = client.get("/foo") {
                header(HttpHeaders.Authorization, "")
            }.status

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun an_application_that_handles_requests_to_foo_with_parameters() = testApplication {
        on("making a request to /foo?key1=value1&key2=value2") {
            routing {
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

            val status = client.get("/foo?key1=value1&key2=value2") {
                header(HttpHeaders.Host, "host.name.com:8888")
            }.status

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }

            val status2 = client.get("/default-port") {
                header(HttpHeaders.Host, "host.name.com")
            }.status

            it("should handle second request") {
                assertEquals(HttpStatusCode.OK, status2)
            }
        }
    }

    @Test
    fun an_application_that_handles_requests_to_root_with_parameters() = testApplication {
        on("making a request to /?key1=value1&key2=value2") {
            routing {
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

            val status = client.get("?key1=value1&key2=value2").status

            it("should handle request") {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }
}
