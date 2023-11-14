/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.webjars

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import org.slf4j.*
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Suppress("DEPRECATION")
class WebjarsTest {

    @Test
    fun resourceNotFound() {
        withTestApplication {
            application.install(Webjars)
            handleRequest(HttpMethod.Get, "/webjars/foo.js").let { call ->
                // Should be handled by some other routing
                assertEquals(HttpStatusCode.NotFound, call.response.status())
            }
        }
    }

    @Test
    fun pathLike() {
        withTestApplication {
            application.install(Webjars)
            application.routing {
                get("/webjars-something/jquery") {
                    call.respondText { "Something Else" }
                }
            }
            handleRequest(HttpMethod.Get, "/webjars-something/jquery").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Something Else", call.response.content)
            }
        }
    }

    @Test
    fun nestedPath() {
        withTestApplication {
            application.install(Webjars) {
                path = "/assets/webjars"
            }
            handleRequest(HttpMethod.Get, "/assets/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun rootPath() {
        withTestApplication {
            application.install(Webjars) {
                path = "/"
            }
            handleRequest(HttpMethod.Get, "/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun rootPath2() {
        withTestApplication {
            application.install(Webjars) {
                path = "/"
            }
            application.routing {
                get("/") { call.respondText("Hello, World") }
            }
            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello, World", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun versionAgnostic() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withGetParameters() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js?param1=value1").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withSpecificVersion() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/3.6.4/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun classifiedAsStatic() {
        var isStatic = false
        var location = ""
        withTestApplication {
            application.install(Webjars)
            application.addPhase(ApplicationSendPipeline.After)
            application.intercept(ApplicationSendPipeline.After) {
                isStatic = call.isStaticContent()
                location = call.attributes[StaticFileLocationProperty]
            }
            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js")

            assertTrue(isStatic, "Should be static file")
            assertEquals(location, "jquery/jquery.js")
        }
    }

    @Test
    fun withConditionalAndCachingHeaders() {
        withTestApplication {
            application.install(Webjars)
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            handleRequest(HttpMethod.Get, "/webjars/jquery/3.6.4/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
                assertNotNull(call.response.headers["Last-Modified"])
                assertEquals("\"3.6.4\"", call.response.headers["Etag"])
                assertEquals("max-age=${90.days.inWholeSeconds}", call.response.headers["Cache-Control"])
            }
        }
    }

    @Test
    fun withConditionalAndCachingHeadersCustom() {
        withTestApplication {
            val date = GMTDate()
            application.install(Webjars) {
                maxAge { 5.seconds }
                lastModified { date }
                etag { "test" }
            }
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            handleRequest(HttpMethod.Get, "/webjars/jquery/3.6.4/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
                assertEquals(date.toHttpDate(), call.response.headers["Last-Modified"])
                assertEquals("\"test\"", call.response.headers["Etag"])
                assertEquals("max-age=5", call.response.headers["Cache-Control"])
            }
        }
    }

    @Test
    fun callHandledBeforeWebjars() {
        val alwaysRespondHello = object : Hook<Unit> {
            override fun install(pipeline: ApplicationCallPipeline, handler: Unit) {
                pipeline.intercept(ApplicationCallPipeline.Setup) {
                    call.respond("Hello")
                }
            }
        }
        val pluginBeforeWebjars = createApplicationPlugin("PluginBeforeWebjars") {
            on(alwaysRespondHello, Unit)
        }

        testApplication {
            install(pluginBeforeWebjars)
            install(Webjars)

            val response = client.get("/webjars/jquery/3.3.1/jquery.js")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello", response.bodyAsText())
            assertNotEquals("application/javascript", response.headers["Content-Type"])
        }
    }
}
