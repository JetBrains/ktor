/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlin.test.*

@Suppress("DEPRECATION")
class RouteTest {

    @Test
    fun testCanInstallPlugin() = testApplication {
        val plugin = createRouteScopedPlugin("test") {
            onCall { call ->
                call.response.header("X-Test", "test")
            }
        }
        routing {
            route("a") {
                get {
                    call.respondText("a")
                }
            }
            route("b") {
                install(plugin)
                get {
                    call.respondText("b")
                }
            }
        }

        assertEquals(null, client.get("/").headers["X-Test"])
        assertEquals("test", client.get("/b").headers["X-Test"])
    }

    @Test
    fun testCanIntercept() = testApplication {
        routing {
            route("a") {
                get {
                    call.respondText("a")
                }
            }
            route("b") {
                intercept(ApplicationCallPipeline.Call) {
                    call.response.header("X-Test", "test")
                }
                get {
                    call.respondText("b")
                }
            }
        }

        assertEquals(null, client.get("/").headers["X-Test"])
        assertEquals("test", client.get("/b").headers["X-Test"])
    }

    @Test
    fun testCanInterceptBeforeAndAfterPhase() = testApplication {
        routing {
            route("a") {
                get {
                    call.respondText("a")
                }
            }
            route("b") {
                val phaseBefore = PipelinePhase("phaseBefore")
                val phaseAfter = PipelinePhase("phaseAfter")
                insertPhaseBefore(ApplicationCallPipeline.Plugins, phaseBefore)
                insertPhaseAfter(ApplicationCallPipeline.Plugins, phaseAfter)
                intercept(phaseAfter) {
                    call.response.header("X-Test", "test-3")
                }
                intercept(ApplicationCallPipeline.Plugins) {
                    call.response.header("X-Test", "test-2")
                }
                intercept(phaseBefore) {
                    call.response.header("X-Test", "test-1")
                }
                get {
                    call.respondText("b")
                }
            }
        }

        assertEquals(null, client.get("/").headers["X-Test"])
        assertEquals(listOf("test-1", "test-2", "test-3"), client.get("/b").headers.getAll("X-Test"))
    }

    @Test
    fun testCanAccessPathAndQueryParameters() = testApplication {
        routing {
            route("path/{id}") {
                get {
                    call.response.header("Path", call.pathParameters.getAll("id")!!.joinToString(","))
                    call.response.header("Query", call.queryParameters.getAll("id")!!.joinToString(","))
                    call.response.header("All", call.parameters.getAll("id")!!.joinToString(","))
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val response = client.get("/path/1?id=2&id=3")
        assertEquals("1", response.headers["Path"])
        assertEquals("2,3", response.headers["Query"])
        assertEquals("2,3,1", response.headers["All"])
    }
}
