/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.testing.*
import io.ktor.server.util.*
import kotlin.test.*

class ServerURLBuilderTest {

    @Test
    fun testPathFirstSlash() {
        val s = url {
            encodedPath = "/a/b"
        }

        assertEquals("http://localhost/a/b", s)
    }

    @Test
    fun testPathFunctionVararg() {
        val s = url {
            path("a", "b")
        }

        assertEquals("http://localhost/a/b", s)
    }

    @Test
    fun testPathFunctionList() {
        val s = url {
            path("a", "b")
        }

        assertEquals("http://localhost/a/b", s)
    }

    @Test
    fun testPathWithSpace() {
        assertEquals("http://localhost/a%20b/c", url { path("a b", "c") })
    }

    @Test
    fun testPathWithPlus() {
        assertEquals("http://localhost/a+b/c", url { path("a+b", "c") })
    }

    @Test
    fun testPathComponentsFirst() {
        val s = url {
            appendPathSegments("asd")
        }

        assertEquals("http://localhost/asd", s)
    }

    @Test
    fun testPathComponentsFunctionVararg() {
        val s = url {
            appendPathSegments("a", "b")
        }

        assertEquals("http://localhost/a/b", s)
    }

    @Test
    fun testPathComponentsFunctionList() {
        val s = url {
            appendPathSegments(listOf("a", "b"))
        }

        assertEquals("http://localhost/a/b", s)
    }

    @Test
    fun testPathComponentsWithSpace() {
        assertEquals("http://localhost/a%20b/c", url { appendPathSegments("a b", "c") })
    }

    @Test
    fun testPathComponentsWithPlus() {
        assertEquals("http://localhost/a+b/c", url { appendPathSegments("a+b", "c") })
    }

    @Test
    fun testPathComponentsWithTrailingSlashes() {
        assertEquals("http://localhost/asd///", url { appendPathSegments("asd///") })
    }

    @Test
    fun testPathComponentsWithLeadingSlashes() {
        assertEquals("http://localhost///asd", url { appendPathSegments("///asd") })
    }

    @Test
    fun testPort() {
        assertEquals("http://localhost", url { port = 80 })
        assertEquals("http://localhost:8080", url { port = 8080 })
        assertEquals("https://localhost:80", url { protocol = URLProtocol.HTTPS; port = 80 })
        assertEquals("https://localhost", url { protocol = URLProtocol.HTTPS; port = 443 })
        assertEquals("https://localhost", url { protocol = URLProtocol.HTTPS })
    }

    @Test
    fun testUserCredentials() {
        assertEquals("http://user:pass@localhost", url { user = "user"; password = "pass" })
        assertEquals("http://user%20name:pass%2B@localhost", url { user = "user name"; password = "pass+" })
    }

    @Test
    fun testParameters() {
        assertEquals("http://localhost?p1=v1", url { parameters.append("p1", "v1") })
        assertEquals("http://localhost?p1=v1&p1=v2", url { parameters.appendAll("p1", listOf("v1", "v2")) })
        assertEquals(
            "http://localhost?p1=v1&p2=v2",
            url {
                parameters.append("p1", "v1")
                parameters.append("p2", "v2")
            }
        )
    }

    @Test
    fun testParametersSpace() {
        assertEquals("http://localhost?p1=v1+space", url { parameters.append("p1", "v1 space") })
    }

    @Test
    fun testParametersPlus() {
        assertEquals("http://localhost?p1=v1%2B.plus", url { parameters.append("p1", "v1+.plus") })
    }

    @Test
    fun testParametersSpaceInParamName() {
        assertEquals("http://localhost?p1%20space=v1", url { parameters.append("p1 space", "v1") })
        assertEquals("http://localhost?p1%2Bspace=v1", url { parameters.append("p1+space", "v1") })
    }

    @Test
    fun testParametersPlusInParamName() {
        assertEquals("http://localhost?p1%2B.plus=v1", url { parameters.append("p1+.plus", "v1") })
    }

    @Test
    fun testParametersEqInParamName() {
        assertEquals("http://localhost?p1%3D.eq=v1", url { parameters.append("p1=.eq", "v1") })
    }

    @Test
    fun testFragment() {
        assertEquals(
            "http://localhost?p=v#a",
            url {
                parameters.append("p", "v")
                fragment = "a"
            }
        )
        assertEquals(
            "http://localhost#a",
            url {
                fragment = "a"
            }
        )
        assertEquals(
            "http://localhost#a%20+%20b",
            url {
                fragment = "a + b"
            }
        )
    }

    @Test
    fun testWithApplication() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("http://my-host/path%20/to?p=v", call.url())
                assertEquals(
                    "http://my-host/path%20/to?p=v",
                    call.url {
                        assertEquals("my-host", host)
                        assertEquals("/path%20/to", encodedPath)
                        assertEquals("v", parameters["p"])
                    }
                )
            }
        }

        client.get("/path%20/to?p=v") {
            header(HttpHeaders.Host, "my-host")
        }
    }

    @Test
    fun testWithApplication2() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                repeat(3) {
                    assertEquals("http://my-host/?p=v", call.url())
                }
            }
        }

        client.get("?p=v") {
            header(HttpHeaders.Host, "my-host")
        }
    }

    @Test
    fun testWithApplicationAndPort() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("http://my-host:8080/path%20/to?p=v", call.url())
                assertEquals(
                    "http://my-host:8080/path%20/to?p=v",
                    call.url {
                        assertEquals(8080, port)
                        assertEquals("my-host", host)
                        assertEquals("/path%20/to", encodedPath)
                        assertEquals("v", parameters["p"])
                    }
                )
            }
        }

        client.get("/path%20/to?p=v") {
            header(HttpHeaders.Host, "my-host:8080")
        }
    }

    @Test
    fun testWithProxy() = testApplication {
        install(XForwardedHeaders)
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("http://special-host:90/", call.url())
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "special-host:90")
        }
    }

    @Test
    fun testWithProxyHttps() = testApplication {
        install(XForwardedHeaders)
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("https://special-host:90/", call.url())
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "special-host:90")
            header(HttpHeaders.XForwardedProto, "https")
        }
    }

    @Test
    fun testWithProxyHttpsDefaultPort() = testApplication {
        install(XForwardedHeaders)
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("https://special-host/", call.url())
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "special-host")
            header(HttpHeaders.XForwardedProto, "https")
        }
    }

    @Test
    fun testWithProxyHttpsWithPortEqualToDefault() = testApplication {
        install(XForwardedHeaders)
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("https://special-host/", call.url())
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "special-host:443")
            header(HttpHeaders.XForwardedProto, "https")
        }
    }
}
