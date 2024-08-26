/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlin.test.*

class RegexRoutingTest {

    @Test
    fun testRoute() = testApplication {
        application {
            routing {
                route(Regex("/(?<number>\\d+)")) {
                    get("/hello") {
                        val number = call.parameters["number"]!!
                        call.respondText(number)
                    }

                    route(Regex("(?<user>\\w+)/(?<login>.+)"), HttpMethod.Get) {
                        handle {
                            val user = call.parameters["user"]!!
                            val login = call.parameters["login"]!!
                            call.respondText("$user $login")
                        }
                    }
                }

                route(Regex("/hello/name-(?<name>.+)"), HttpMethod.Get) {
                    handle {
                        val name = call.parameters["name"]!!
                        call.respondText(name)
                    }
                }
            }
        }

        val route = client.get("/123/hello").bodyAsText().toInt()
        assertEquals(123, route)
        val routeWithMethod = client.get("/hello/name-Abc12").bodyAsText()
        assertEquals("Abc12", routeWithMethod)
        val combineRoutes = client.get("/456/qwe/rty").bodyAsText()
        assertEquals("qwe rty", combineRoutes)
    }

    @Test
    fun testGet() = testApplication {
        application {
            routing {
                route("/hello") {
                    get(Regex("/(?<number>\\d+)/")) {
                        val number = call.parameters["number"]!!
                        call.respondText(number)
                    }
                }

                get(Regex("^(?<name>.+)/tags/list$")) {
                    val name = call.parameters["name"]!!
                    call.respond(HttpStatusCode.OK, name)
                }
            }
        }

        val routeWithGet = client.get("/hello/123456/").bodyAsText().toInt()
        assertEquals(123456, routeWithGet)
        val get = client.get("tag_1/tags/list").bodyAsText()
        assertEquals("tag_1", get)
    }

    @Test
    fun testPost() = testApplication {
        application {
            routing {
                post(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }

                post<String>(Regex("/.+/hello")) {
                    call.respondText(it)
                }
            }
        }

        val post = client.post("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", post)
        val typedPost = client.post("/qwe/rty/hello") {
            setBody("abc")
        }.bodyAsText()
        assertEquals("abc", typedPost)
    }

    @Test
    fun testPut() = testApplication {
        application {
            routing {
                put(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }

                put<String>(Regex("/.+/hello")) {
                    call.respondText(it)
                }
            }
        }

        val put = client.put("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", put)
        val typedPut = client.put("/qwe/rty/hello") {
            setBody("abc")
        }.bodyAsText()
        assertEquals("abc", typedPut)
    }

    @Test
    fun testPatch() = testApplication {
        application {
            routing {
                patch(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }

                patch<String>(Regex("/.+/hello")) {
                    call.respondText(it)
                }
            }
        }

        val patch = client.patch("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", patch)
        val typedPatch = client.patch("/qwe/rty/hello") {
            setBody("abc")
        }.bodyAsText()
        assertEquals("abc", typedPatch)
    }

    @Test
    fun testDelete() = testApplication {
        application {
            routing {
                delete(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }
            }
        }

        val delete = client.delete("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", delete)
    }

    @Test
    fun testHead() = testApplication {
        application {
            routing {
                head(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }
            }
        }

        val head = client.head("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", head)
    }

    @Test
    fun testOptions() = testApplication {
        application {
            routing {
                options(Regex("/tag/(?<name>.+)")) {
                    val name = call.parameters["name"]!!
                    call.respondText(name)
                }
            }
        }

        val options = client.options("/tag/abc 123").bodyAsText()
        assertEquals("abc 123", options)
    }

    @Test
    fun testOnlyValidGroups() = testApplication {
        application {
            routing {
                get(Regex("/\\(?<notGroup>")) {
                    assertEquals(0, call.parameters.names().size)
                }
                get(Regex("/(?<group1>ok)")) {
                    assertEquals(1, call.parameters.names().size)
                }

                get(Regex("(?<a>\\(\\))")) {
                    val a = call.parameters["a"]
                    assertEquals("()", a)
                }
            }
        }

        client.get("/(<notGroup>")
        client.get("/ok")
        client.get("/()")
    }

    @Test
    fun testUnneededEscaping() = testApplication {
        application {
            routing {
                get(Regex("""\/abc""")) {
                    call.respondText("OK")
                }
                get(Regex("""def\/""")) {
                    call.respondText("OK")
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/abc").status)
        assertEquals(HttpStatusCode.OK, client.get("abc").status)
        assertEquals(HttpStatusCode.OK, client.get("def/").status)

        assertEquals(HttpStatusCode.NotFound, client.get("""\/abc""").status)
        assertEquals(HttpStatusCode.NotFound, client.get("""def\/""").status)
    }

    @Test
    fun testNestedRegex() = testApplication {
        application {
            routing {
                route(Regex("/abc/")) {
                    get(Regex("def")) {
                        call.respondText("/abc/def")
                    }

                    handle {
                        call.respondText("/abc/")
                    }
                }
                route(Regex("qwe")) {
                    get(Regex("/rty/")) {
                        call.respondText("qwe/rty/")
                    }

                    handle {
                        call.respondText("qwe")
                    }
                }
                route(Regex("a")) {
                    route(Regex("b")) {
                        get(Regex("c")) {
                            call.respondText("a/b/c")
                        }
                    }
                }
            }
        }

        assertEquals("/abc/def", client.get("/abc/def").bodyAsText())
        assertEquals("/abc/def", client.get("abc/def").bodyAsText())
        assertEquals("/abc/", client.get("abc/").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/abc").status)

        assertEquals("qwe/rty/", client.get("qwe/rty/").bodyAsText())
        assertEquals("qwe", client.get("/qwe").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("qwe/rty").status)
        assertEquals(HttpStatusCode.NotFound, client.get("qwe/").status)

        assertEquals("a/b/c", client.get("/a/b/c").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("a/b/c/").status)
    }

    @Test
    fun testWithTrailingSlash() = testApplication {
        application {
            install(IgnoreTrailingSlash)

            routing {
                get(Regex("/abc/")) {
                    call.respondText("/abc/")
                }

                route(Regex("qwe")) {
                    get(Regex("/rty/")) {
                        call.respondText("qwe/rty/")
                    }

                    handle {
                        call.respondText("qwe")
                    }
                }
            }
        }

        assertEquals("/abc/", client.get("abc").bodyAsText())
        assertEquals("/abc/", client.get("/abc/").bodyAsText())

        assertEquals("qwe/rty/", client.get("qwe/rty").bodyAsText())
        assertEquals("qwe", client.get("qwe/").bodyAsText())
    }

    @Test
    fun testNotGroup() = testApplication {
        application {
            routing {
                get(Regex("""\(?<a>\)""")) {
                    call.respondText("ok")
                }
            }
        }

        assertEquals("ok", client.get("/<a>)").bodyAsText())
        assertEquals("ok", client.get("(<a>)").bodyAsText())
    }
}
