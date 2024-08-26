/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RouteTest {

    @Test
    fun testToStringSimple() {
        val root = RoutingNode(
            parent = null,
            selector = PathSegmentConstantRouteSelector("root"),
            environment = createTestEnvironment()
        )
        val simpleChild = RoutingNode(
            parent = root,
            selector = PathSegmentConstantRouteSelector("simpleChild"),
            environment = createTestEnvironment()
        )
        val simpleGrandChild =
            RoutingNode(
                parent = simpleChild,
                selector = PathSegmentConstantRouteSelector("simpleGrandChild"),
                environment = createTestEnvironment()
            )

        val slashChild = RoutingNode(
            parent = root,
            selector = TrailingSlashRouteSelector,
            environment = createTestEnvironment()
        )
        val slashGrandChild = RoutingNode(
            parent = slashChild,
            selector = TrailingSlashRouteSelector,
            environment = createTestEnvironment()
        )
        val simpleChildInSlash = RoutingNode(
            parent = slashGrandChild,
            selector = PathSegmentConstantRouteSelector("simpleChildInSlash"),
            environment = createTestEnvironment()
        )
        val slashChildInSimpleChild = RoutingNode(
            parent = simpleChildInSlash,
            selector = TrailingSlashRouteSelector,
            environment = createTestEnvironment()
        )

        assertEquals("/root", root.toString())
        assertEquals("/root/simpleChild", simpleChild.toString())
        assertEquals("/root/simpleChild/simpleGrandChild", simpleGrandChild.toString())
        assertEquals("/root/", slashChild.toString())
        assertEquals("/root/", slashGrandChild.toString())
        assertEquals("/root/simpleChildInSlash", simpleChildInSlash.toString())
        assertEquals("/root/simpleChildInSlash/", slashChildInSimpleChild.toString())
    }

    @Test
    fun testCreateChildKeepsDevelopmentMode() {
        val root = RoutingNode(
            parent = null,
            selector = PathSegmentConstantRouteSelector("root"),
            developmentMode = true,
            environment = createTestEnvironment()
        )
        val simpleChild = root.createChild(PathSegmentConstantRouteSelector("simpleChild"))
        assertTrue(root.developmentMode)
        assertTrue(simpleChild.developmentMode)
    }

    @Test
    fun testGetAllRoutes() = testApplication {
        application {
            val root = routing {
                route("/shop") {
                    route("/customer") {
                        get("/{id}") {
                            call.respondText("OK")
                        }
                        post("/new") { }
                    }

                    route("/order") {
                        route("/shipment") {
                            get { }
                            post {
                                call.respondText("OK")
                            }
                            put {
                                call.respondText("OK")
                            }
                        }
                    }
                }

                route("/info", HttpMethod.Get) {
                    post("new") {}

                    handle {
                        call.respondText("OK")
                    }
                }
            }

            val endpoints = root.getAllRoutes()
            assertTrue { endpoints.size == 7 }
            val expected = setOf(
                "/shop/customer/{id}/(method:GET)",
                "/shop/customer/new/(method:POST)",
                "/shop/order/shipment/(method:GET)",
                "/shop/order/shipment/(method:PUT)",
                "/shop/order/shipment/(method:POST)",
                "/info/(method:GET)",
                "/info/(method:GET)/new/(method:POST)"
            )
            assertEquals(expected, endpoints.map { it.toString() }.toSet())
        }
    }
}
