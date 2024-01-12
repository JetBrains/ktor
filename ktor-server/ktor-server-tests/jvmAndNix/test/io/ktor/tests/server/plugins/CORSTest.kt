/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class CORSTest {

    @Test
    fun testNoOriginHeader() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {}.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testWrongOriginHeader() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "invalid-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testWrongOriginHeaderIsEmpty() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequest() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestSubrouteInstall() = testApplication {
        routing {
            route("/1") {
                install(CORS) {
                    allowHost("my-host")
                }
                get {
                    call.respond("OK")
                }
            }
            route("/2") {
                get {
                    call.respond("OK")
                }
            }
        }

        client.get("/1") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://my-host", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", response.bodyAsText())
        }

        client.get("/1") {
            header(HttpHeaders.Origin, "http://other-host")
        }.let { response ->
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

        client.get("/2") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", response.bodyAsText())
        }

        client.get("/2") {
            header(HttpHeaders.Origin, "http://other-host")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", response.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequestPort1() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:80")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host:80", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
                assertEquals(HttpHeaders.Origin, call.response.headers[HttpHeaders.Vary])
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:90")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestPort2() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host:80")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:80")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host:80", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestExposed() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host")
                exposeHeader(HttpHeaders.ETag)
                exposeHeader(HttpHeaders.Vary)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(
                    setOf(HttpHeaders.ETag, HttpHeaders.Vary),
                    call.response.headers[HttpHeaders.AccessControlExposeHeaders]?.split(", ")?.toSet()
                )
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestHttps() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("https://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestSubDomains() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host", subDomains = listOf("www"))
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://www.my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://www.my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other.my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStar() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertNull(call.response.headers[HttpHeaders.Vary])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleNullAllowCredentials() {
        testApplication {
            application {
                install(CORS) {
                    anyHost()
                    allowCredentials = true
                }

                routing {
                    get("/") {
                        call.respond("OK")
                    }
                }
            }
            client.get("/") {
                header(HttpHeaders.Origin, "null")
            }.let {
                assertEquals(HttpStatusCode.OK, it.call.response.status)
                assertEquals("null", it.call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", it.call.response.bodyAsText())
            }
        }
    }

    @Test
    fun testSimpleNull() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "null")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStarCredentials() {
        testApplication {
            application {
                install(CORS) {
                    anyHost()
                    allowCredentials = true
                }

                routing {
                    get("/") {
                        call.respond("OK")
                    }
                }
            }
            client.get("/") {
                header(HttpHeaders.Origin, "http://my-host")
            }.let {
                assertEquals(HttpStatusCode.OK, it.call.response.status)
                assertEquals("http://my-host", it.call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("true", it.call.response.headers[HttpHeaders.AccessControlAllowCredentials])
                assertEquals(HttpHeaders.Origin, it.call.response.headers[HttpHeaders.Vary])
                assertEquals("OK", it.call.response.bodyAsText())
            }
        }
    }

    @Test
    fun testSameOriginEnabled() {
        withTestApplication {
            application.install(CORS)

            application.routing {
                get("/") {
                    call.respond("OK")
                }
                delete("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://localhost")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(null, call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Delete, "/") {
                addHeader(HttpHeaders.Origin, "http://localhost")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(null, call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://localhost:8080")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }
        }
    }

    @Test
    fun testSameOriginDisabled() {
        withTestApplication {
            application.install(CORS) {
                allowSameOrigin = false
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://localhost")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://localhost:8080")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }
        }
    }

    @Test
    fun testMultipleDomainsOriginNotSupported() {
        // the specification is not clear whether we should support multiple domains Origin header and how do we validate them
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://host1 http://host2")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testNonSimpleContentTypeNotAllowedByDefault() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
            }

            application.routing {
                post("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.ContentType, "application/json") // non-simple Content-Type value
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.ContentType, "text/plain") // simple Content-Type value
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.ContentType, "text/plain;charset=utf-8") // still simple Content-Type value
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testPreFlightMultipleHeadersRegression(): Unit = withTestApplication {
        application.install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Range)
        }

        application.routing {
            get("/") {
                call.respond("OK")
            }
        }

        // simple `Content-Type` request header is not allowed by default
        handleRequest(HttpMethod.Options, "/") {
            addHeader(HttpHeaders.Origin, "http://my-host")
            addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            addHeader(
                HttpHeaders.AccessControlRequestHeaders,
                "${HttpHeaders.Accept},${HttpHeaders.ContentType}"
            )
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.response.status())
        }
    }

    @Test
    fun testPreFlight() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.Range)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(null, call.response.headers[HttpHeaders.Vary])
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "PUT")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            // simple request header is always allowed
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Accept)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
            }

            // simple `Content-Type` request header is not allowed by default
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ContentType)
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            // custom header that is not allowed
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ALPN)
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            // custom header that is allowed
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Range)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
                assertTrue {
                    HttpHeaders.Range in call.response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty()
                }
            }
        }
    }

    @Test
    fun testPreFlightCustomMethod() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Delete)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            // non-simple allowed method
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "DELETE")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
            }

            // non-simple not allowed method
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "PUT")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertEquals(null, call.response.headers[HttpHeaders.AccessControlAllowOrigin])
            }

            // simple method
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
            }
        }
    }

    @Test
    fun testPreFlightAllowedContentTypes() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowNonSimpleContentTypes = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            // no content type specified
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("Content-Type", call.response.headers[HttpHeaders.AccessControlAllowHeaders])
            }

            // content type is specified
            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "content-type")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("Content-Type", call.response.headers[HttpHeaders.AccessControlAllowHeaders])
            }
        }
    }

    @Test
    fun testPreflightCustomHost() {
        withTestApplication {
            application.install(CORS) {
                allowHost("my-host")
                allowNonSimpleContentTypes = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("Content-Type", call.response.headers[HttpHeaders.AccessControlAllowHeaders])
                assertEquals(HttpHeaders.Origin, call.response.headers[HttpHeaders.Vary])
            }
        }
    }

    @Test
    fun testPreflightCustomMaxAge() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                maxAgeInSeconds = 100
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("100", call.response.headers[HttpHeaders.AccessControlMaxAge])
            }
        }
    }

    @Test
    fun testAnyHeaderWithPrefix() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeadersPrefixed("custom-")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "custom-header1, custom-header2")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(
                    "custom-header1, custom-header2",
                    call.response.headers.get(HttpHeaders.AccessControlAllowHeaders)
                )
            }
        }
    }

    @Test
    fun testAnyHeaderWithPrefixRequestIgnoresCase() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("Сustom1-")
            allowHeadersPrefixed("custom2-")
        }
        routing {
            post("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "сustom1-header")
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Custom2-header")
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }

    @Test
    fun testAnyHeaderWithPrefixMergesHeadersWithConfiguration() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeadersPrefixed("custom-")
                allowHeader(HttpHeaders.Range)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "custom-header1, custom-header2")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(
                    "Range, custom-header1, custom-header2",
                    call.response.headers.get(HttpHeaders.AccessControlAllowHeaders)
                )
            }
        }
    }

    @Test
    fun testAnyHeaderWithPrefixWithNoControlRequestHeaders() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeadersPrefixed("custom-")
                allowHeader(HttpHeaders.Range)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Range", call.response.headers.get(HttpHeaders.AccessControlAllowHeaders))
            }
        }
    }

    @Test
    fun testAnyHeaderWithPrefixRequestIsRejectedWhenHeaderDoesNotMatchPrefix() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeadersPrefixed("custom-")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "x-header1")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }
        }
    }

    @Test
    fun testAnyHeaderSupportsMultipleHeaderPrefixes() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeadersPrefixed("custom1-")
                allowHeadersPrefixed("custom2-")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "custom1-header, custom2-header")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(
                    "custom1-header, custom2-header",
                    call.response.headers.get(HttpHeaders.AccessControlAllowHeaders)
                )
            }
        }
    }

    @Test
    fun testAnyHeaderCanSpecifyACustomPredicate() {
        withTestApplication {
            application.install(CORS) {
                anyHost()
                allowHeaders { name -> name.startsWith("custom-matcher") }
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "custom-matcher-header")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("custom-matcher-header", call.response.headers.get(HttpHeaders.AccessControlAllowHeaders))
            }
        }
    }

    @Test
    fun testEmptyAccessControlRequestHeaders() {
        withTestApplication {
            application.install(CORS) {
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.XForwardedProto)
                allowHost("host")
                allowSameOrigin = false
                allowCredentials = true
                allowNonSimpleContentTypes = true
            }

            application.routing {
                post("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "POST")
                addHeader(HttpHeaders.AccessControlRequestHeaders, "")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(
                    "Content-Type, X-Forwarded-Proto",
                    call.response.headers.get(HttpHeaders.AccessControlAllowHeaders)
                )
            }
        }
    }

    @Test
    fun originValidation() = testApplication {
        install(CORS) {
            allowSameOrigin = false
            anyHost()
        }
        routing {
            get("") { call.respond("OK") }
        }

        val client = createClient { expectSuccess = false }
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "hyp-hen://host") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "plus+://host") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "do.t://host") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "digits11://host") }.status
        )

        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "a()://host") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "1abc://host") }.status
        )
    }

    @Test
    fun originWithWildcard() = testApplication {
        install(CORS) {
            allowSameOrigin = true
            allowHost("domain.com")
            allowHost("*.domain.com")
        }
        routing {
            get("") { call.respond("OK") }
        }

        val client = createClient { expectSuccess = false }
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://www.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://foo.bar.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get { headers.append(HttpHeaders.Origin, "http://domain.net") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "https://domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "https://www.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get { headers.append(HttpHeaders.Origin, "https://domain.net") }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get { headers.append(HttpHeaders.Origin, "sftp://domain.com") }.status
        )
    }

    @Test
    fun originWithWildcardAndSubdomain() = testApplication {
        install(CORS) {
            allowSameOrigin = true
            allowHost("domain.com", subDomains = listOf("foo", "*.bar"))
        }
        routing {
            get("") { call.respond("OK") }
        }

        val client = createClient { expectSuccess = false }
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://foo.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://foo.bar.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get { headers.append(HttpHeaders.Origin, "http://anything.bar.domain.com") }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get { headers.append(HttpHeaders.Origin, "http://invalid.foo.domain.com") }.status
        )
    }

    @Test
    fun invalidOriginWithWildcard() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            ("domain.com*" to messageWildcardInFrontOfDomain),
            ("domain.com*." to messageWildcardInFrontOfDomain),
            ("*." to messageWildcardInFrontOfDomain),
            ("**" to messageWildcardInFrontOfDomain),
            ("*.*." to messageWildcardInFrontOfDomain),
            ("*.*.domain.com" to messageWildcardOnlyOnce),
            ("*.foo*.domain.com" to messageWildcardOnlyOnce),
        ).forEach { (host, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for this host '$host'"
            ) {
                testApplication {
                    install(CORS) { allowHost(host) }
                }
            }
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun originWithWildcardAndSubDomain() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("foo*.") to messageWildcardInFrontOfDomain),
            (listOf("*.foo*.bar") to messageWildcardOnlyOnce),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                testApplication {
                    install(CORS) { allowHost("domain.com", subDomains = subDomains) }
                }
            }

            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun invalidOriginWithWildcardAndSubDomain() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("*.foo") to messageWildcardOnlyOnce),
            (listOf("*") to messageWildcardInFrontOfDomain),
            (listOf("foo") to messageWildcardInFrontOfDomain),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                testApplication {
                    install(CORS) { allowHost("*.domain.com", subDomains = subDomains) }
                }
            }
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun testOriginPredicatesSimpleRequest() {
        withTestApplication {
            application.install(CORS) {
                allowOrigins { it == "https://allowed-host" }
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://allowed-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("https://allowed-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://forbidden-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://allowed-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testOriginPredicatesRegex() {
        withTestApplication {
            application.install(CORS) {
                allowOrigins { it.matches(Regex("^https?://host\\.(?:com|org)$")) }
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://host.com")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("https://host.com", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://host.org")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://host.org", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://host.net")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }
}
