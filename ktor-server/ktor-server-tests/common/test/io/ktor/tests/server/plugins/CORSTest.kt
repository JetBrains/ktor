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
import kotlinx.coroutines.test.*
import kotlin.test.*

class CORSTest {

    @Test
    fun testNoOriginHeader() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {}.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertNull(call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testWrongOriginHeader() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "invalid-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertNull(call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testWrongOriginHeaderIsEmpty() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertNull(call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequest() = testApplication {
        install(CORS) {
            allowHost("my-host")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://other-host")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
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
    fun testSimpleRequestPort1() = testApplication {
        install(CORS) {
            allowHost("my-host")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host:80")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host:80", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
            assertEquals(HttpHeaders.Origin, call.headers[HttpHeaders.Vary])
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host:90")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequestPort2() = testApplication {
        install(CORS) {
            allowHost("my-host:80")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host:80")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host:80", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequestExposed() = testApplication {
        install(CORS) {
            allowHost("my-host")
            exposeHeader(HttpHeaders.ETag)
            exposeHeader(HttpHeaders.Vary)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals(
                setOf(HttpHeaders.ETag, HttpHeaders.Vary),
                call.headers[HttpHeaders.AccessControlExposeHeaders]?.split(", ")?.toSet()
            )
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequestHttps() = testApplication {
        install(CORS) {
            allowHost("my-host")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "https://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("https://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://other-host")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleRequestSubDomains() = testApplication {
        install(CORS) {
            allowHost("my-host", subDomains = listOf("www"))
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://www.my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://www.my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://other.my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleStar() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://my-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertNull(call.headers[HttpHeaders.Vary])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleNullAllowCredentials() = testApplication {
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
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("null", it.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", it.bodyAsText())
        }
    }

    @Test
    fun testSimpleNull() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "null")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testSimpleStarCredentials() = testApplication {
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
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("http://my-host", it.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("true", it.headers[HttpHeaders.AccessControlAllowCredentials])
            assertEquals(HttpHeaders.Origin, it.headers[HttpHeaders.Vary])
            assertEquals("OK", it.bodyAsText())
        }
    }

    @Test
    fun testSameOriginEnabled() = testApplication {
        install(CORS)

        routing {
            get("/") {
                call.respond("OK")
            }
            delete("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(null, call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.delete("/") {
            header(HttpHeaders.Origin, "http://localhost")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(null, call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost:8080")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }
    }

    @Test
    fun testSameOriginDisabled() = testApplication {
        install(CORS) {
            allowSameOrigin = false
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost:8080")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }
    }

    // the specification is not clear whether we should support multiple domains Origin header and how do we validate them
    @Test
    fun testMultipleDomainsOriginNotSupported() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://host1 http://host2")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertNull(call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun testNonSimpleContentTypeNotAllowedByDefault() = testApplication {
        install(CORS) {
            anyHost()
        }

        routing {
            post("/") {
                call.respond("OK")
            }
        }

        client.post("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.ContentType, "application/json") // non-simple Content-Type value
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }

        client.post("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.ContentType, "text/plain") // simple Content-Type value
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }

        client.post("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.ContentType, "text/plain;charset=utf-8") // still simple Content-Type value
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testPreFlightMultipleHeadersRegression() = testApplication {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Range)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        // simple `Content-Type` request header is not allowed by default
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(
                HttpHeaders.AccessControlRequestHeaders,
                "${HttpHeaders.Accept},${HttpHeaders.ContentType}"
            )
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }
    }

    @Test
    fun testPreFlight() = testApplication {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Range)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals(null, call.headers[HttpHeaders.Vary])
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }

        // simple request header is always allowed
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Accept)
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertTrue { call.headers.getAll(HttpHeaders.AccessControlAllowHeaders)!!.isNotEmpty() }
        }

        // simple `Content-Type` request header is not allowed by default
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ContentType)
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }

        // custom header that is not allowed
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ALPN)
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }

        // custom header that is allowed
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Range)
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertTrue { call.headers.getAll(HttpHeaders.AccessControlAllowHeaders)!!.isNotEmpty() }
            assertTrue { HttpHeaders.Range in call.headers.getAll(HttpHeaders.AccessControlAllowHeaders).orEmpty() }
        }
    }

    @Test
    fun testPreFlightCustomMethod() = testApplication {
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Delete)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        // non-simple allowed method
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "DELETE")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
        }

        // non-simple not allowed method
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals(null, call.headers[HttpHeaders.AccessControlAllowOrigin])
        }

        // simple method
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
        }
    }

    @Test
    fun testPreFlightAllowedContentTypes() = testApplication {
        install(CORS) {
            anyHost()
            allowNonSimpleContentTypes = true
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        // no content type specified
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Content-Type", call.headers[HttpHeaders.AccessControlAllowHeaders])
        }

        // content type is specified
        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("*", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Content-Type", call.headers[HttpHeaders.AccessControlAllowHeaders])
        }
    }

    @Test
    fun testPreflightCustomHost() = testApplication {
        install(CORS) {
            allowHost("my-host")
            allowNonSimpleContentTypes = true
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://my-host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("http://my-host", call.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Content-Type", call.headers[HttpHeaders.AccessControlAllowHeaders])
            assertEquals(HttpHeaders.Origin, call.headers[HttpHeaders.Vary])
        }
    }

    @Test
    fun testPreflightCustomMaxAge() = testApplication {
        install(CORS) {
            anyHost()
            maxAgeInSeconds = 100
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("100", call.headers[HttpHeaders.AccessControlMaxAge])
        }
    }

    @Test
    fun testAnyHeaderWithPrefix() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("custom-")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "custom-header1, custom-header2")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "custom-header1, custom-header2",
                call.headers[HttpHeaders.AccessControlAllowHeaders]
            )
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
    fun testAnyHeaderWithPrefixMergesHeadersWithConfiguration() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("custom-")
            allowHeader(HttpHeaders.Range)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "custom-header1, custom-header2")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "Range, custom-header1, custom-header2",
                call.headers[HttpHeaders.AccessControlAllowHeaders]
            )
        }
    }

    @Test
    fun testAnyHeaderWithPrefixWithNoControlRequestHeaders() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("custom-")
            allowHeader(HttpHeaders.Range)
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("Range", call.headers[HttpHeaders.AccessControlAllowHeaders])
        }
    }

    @Test
    fun testAnyHeaderWithPrefixRequestIsRejectedWhenHeaderDoesNotMatchPrefix() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("custom-")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "x-header1")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
        }
    }

    @Test
    fun testAnyHeaderSupportsMultipleHeaderPrefixes() = testApplication {
        install(CORS) {
            anyHost()
            allowHeadersPrefixed("custom1-")
            allowHeadersPrefixed("custom2-")
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "custom1-header, custom2-header")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "custom1-header, custom2-header",
                call.headers[HttpHeaders.AccessControlAllowHeaders]
            )
        }
    }

    @Test
    fun testAnyHeaderCanSpecifyACustomPredicate() = testApplication {
        install(CORS) {
            anyHost()
            allowHeaders { name -> name.startsWith("custom-matcher") }
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "custom-matcher-header")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("custom-matcher-header", call.headers[HttpHeaders.AccessControlAllowHeaders])
        }
    }

    @Test
    fun testEmptyAccessControlRequestHeaders() = testApplication {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.XForwardedProto)
            allowHost("host")
            allowSameOrigin = false
            allowCredentials = true
            allowNonSimpleContentTypes = true
        }

        routing {
            post("/") {
                call.respond("OK")
            }
        }

        client.options("/") {
            header(HttpHeaders.Origin, "http://host")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "Content-Type, X-Forwarded-Proto",
                call.headers[HttpHeaders.AccessControlAllowHeaders]
            )
        }
    }

    @Test
    fun testCorsValidationWithTrailingSlashOrigin() = testApplication {
        application {
            install(CORS) {}
            routing {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost:3000/")
        }.let {
            assertEquals(HttpStatusCode.Forbidden, it.status)
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://localhost:3000")
        }.let {
            assertEquals(HttpStatusCode.Forbidden, it.status)
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
            client.get {
                headers.append(
                    HttpHeaders.Origin,
                    "http://anything.bar.domain.com"
                )
            }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get {
                headers.append(
                    HttpHeaders.Origin,
                    "http://invalid.foo.domain.com"
                )
            }.status
        )
    }

    @Test
    fun invalidOriginWithWildcard() = runTest {
        val messageWildcardInFrontOfDomain =
            "wildcard must appear in front of the domain, e.g. *.domain.com"
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
                runTestApplication {
                    install(CORS) { allowHost(host) }
                }
            }
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun originWithWildcardAndSubDomain() = runTest {
        val messageWildcardInFrontOfDomain =
            "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("foo*.") to messageWildcardInFrontOfDomain),
            (listOf("*.foo*.bar") to messageWildcardOnlyOnce),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                runTestApplication {
                    install(CORS) { allowHost("domain.com", subDomains = subDomains) }
                }
            }

            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun invalidOriginWithWildcardAndSubDomain() = runTest {
        val messageWildcardInFrontOfDomain =
            "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("*.foo") to messageWildcardOnlyOnce),
            (listOf("*") to messageWildcardInFrontOfDomain),
            (listOf("foo") to messageWildcardInFrontOfDomain),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                runTestApplication {
                    install(CORS) { allowHost("*.domain.com", subDomains = subDomains) }
                }
            }
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun testOriginPredicatesSimpleRequest() = testApplication {
        install(CORS) {
            allowOrigins { it == "https://allowed-host" }
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "https://allowed-host")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "https://allowed-host",
                call.headers[HttpHeaders.AccessControlAllowOrigin]
            )
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "https://forbidden-host")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://allowed-host")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }
    }

    @Test
    fun testOriginPredicatesRegex() = testApplication {
        install(CORS) {
            allowOrigins { it.matches(Regex("^https?://host\\.(?:com|org)$")) }
        }

        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Origin, "https://host.com")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "https://host.com",
                call.headers[HttpHeaders.AccessControlAllowOrigin]
            )
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "http://host.org")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals(
                "http://host.org",
                call.headers[HttpHeaders.AccessControlAllowOrigin]
            )
            assertEquals("OK", call.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Origin, "https://host.net")
        }.let { call ->
            assertEquals(HttpStatusCode.Forbidden, call.status)
            assertEquals("", call.bodyAsText())
        }
    }
}
