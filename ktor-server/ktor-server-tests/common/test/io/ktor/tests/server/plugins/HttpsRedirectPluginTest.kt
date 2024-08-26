/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class HttpsRedirectPluginTest {

    private fun ApplicationTestBuilder.noRedirectsClient() = createClient { followRedirects = false }

    @Test
    fun testRedirect() = testApplication {
        install(HttpsRedirect)
        routing {
            get("/") {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/").let { call ->
            assertEquals(HttpStatusCode.MovedPermanently, call.status)
            assertEquals("https://localhost/", call.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun testRedirectHttps() = testApplication {
        install(XForwardedHeaders)
        install(HttpsRedirect)
        routing {
            get("/") {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testDirectPathAndQuery() = testApplication {
        install(HttpsRedirect)
        application {
            intercept(ApplicationCallPipeline.Fallback) {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/some/path?q=1").let { call ->
            assertEquals(HttpStatusCode.MovedPermanently, call.status)
            assertEquals("https://localhost/some/path?q=1", call.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun testDirectPathAndQueryWithCustomPort() = testApplication {
        install(HttpsRedirect) {
            sslPort = 8443
        }
        application {
            intercept(ApplicationCallPipeline.Fallback) {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/some/path?q=1").let { call ->
            assertEquals(HttpStatusCode.MovedPermanently, call.status)
            assertEquals("https://localhost:8443/some/path?q=1", call.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun testRedirectHttpsPrefixExemption() = testApplication {
        install(HttpsRedirect) {
            excludePrefix("/exempted")
        }
        routing {
            get("/exempted/path") {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/nonexempted").let { call ->
            assertEquals(HttpStatusCode.MovedPermanently, call.status)
        }

        noRedirectsClient().get("/exempted/path").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }

    @Test
    fun testRedirectHttpsSuffixExemption() = testApplication {
        install(HttpsRedirect) {
            excludeSuffix("exempted")
        }
        routing {
            get("/path/exempted") {
                call.respond("ok")
            }
        }

        noRedirectsClient().get("/exemptednot").let { call ->
            assertEquals(HttpStatusCode.MovedPermanently, call.status)
        }

        noRedirectsClient().get("/path/exempted").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
        }
    }
}
