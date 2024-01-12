/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingBuildTest {
    @Test
    fun `build routing`() {
        fun On.itShouldHaveSpecificStructure(entry: RouteNode) {
            it("should have single child at root") {
                assertEquals(1, entry.children.size)
            }
            it("should have correct parent for single child") {
                assertEquals(entry, entry.children[0].parent)
            }
            it("should have child of type UriPartConstantRoutingSelector") {
                assertTrue(entry.children[0].selector is PathSegmentConstantRouteSelector)
            }
            it("should have child with name 'foo'") {
                assertEquals("foo", (entry.children[0].selector as PathSegmentConstantRouteSelector).value)
            }
            it("should have single child at second level") {
                assertEquals(1, entry.children[0].children.size)
            }
            it("should have second level child of type UriPartOptionalParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is PathSegmentOptionalParameterRouteSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals(
                    "new",
                    (entry.children[0].children[0].selector as PathSegmentOptionalParameterRouteSelector).name
                )
            }
        }

        on("adding routing rules manually") {
            val entry = routing()
            entry.createChild(PathSegmentConstantRouteSelector("foo"))
                .createChild(PathSegmentOptionalParameterRouteSelector("new"))
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string") {
            val entry = routing()
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in nested blocks") {
            val entry = routing()
            entry.route("/foo") {
                route("/{new?}") { }
            }
            itShouldHaveSpecificStructure(entry)
        }

        on("adding routing from string in separate blocks") {
            val entry = routing()
            entry.route("/foo") { }
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }

        on("creating route with non-optional parameter") {
            val entry = routing()
            entry.route("/foo/{new}") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is PathSegmentParameterRouteSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals("new", (entry.children[0].children[0].selector as PathSegmentParameterRouteSelector).name)
            }
        }

        on("creating route with surrounded parameter") {
            val entry = routing()
            entry.route("/foo/a{new}b") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                val selector = entry.children[0].children[0].selector as? PathSegmentParameterRouteSelector
                assertNotNull(selector)
                assertEquals("new", selector.name)
                assertEquals("a", selector.prefix)
                assertEquals("b", selector.suffix)
            }
        }

        on("creating route with wildcard") {
            val entry = routing()
            entry.route("/foo/*") { }
            it("should have second level child of type UriPartWildcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is PathSegmentWildcardRouteSelector)
            }
        }
        on("creating route with tailcard") {
            val entry = routing()
            entry.route("/foo/{...}") { }
            it("should have second level child of type UriPartTailcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is PathSegmentTailcardRouteSelector)
            }
        }
    }
}
