/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.cookiesTest() {
    routing {
        route("cookies") {
            get {
                val cookie = Cookie("hello-cookie", "my-awesome-value", domain = "127.0.0.1")
                call.response.cookies.append(cookie)

                call.respond("Done")
            }
            get("dump") {
                val text = call.request.cookies.rawCookies.entries.joinToString()
                call.respondText("Cookies: $text")
            }
            get("/update-user-id") {
                val id = call.request.cookies["id"]?.toInt() ?: let {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respondText("Forbidden")
                    return@get
                }

                with(call.response.cookies) {
                    append(Cookie("id", (id + 1).toString(), domain = "127.0.0.1", path = "/"))
                    append(Cookie("user", "ktor", domain = "127.0.0.1", path = "/"))
                }

                call.respond("Done")
            }
            get("/multiple") {
                val cookies = call.request.cookies
                val first = cookies["first"] ?: error("First cookie not found")
                val second = cookies["second"] ?: error("Second cookie not found")

                check("first-cookie" == first)
                check("second-cookie" == second)
                call.respond("Multiple done")
            }
            get("/withPath") {
                val cookie = Cookie("marker", "value", path = "cookies/withPath/")
                call.response.cookies.append(cookie)
                call.respond("OK")
            }
            get("/withPath/something") {
                val cookies = call.request.cookies
                if (cookies["marker"] == "value") {
                    call.respond("OK")
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            get("/foo") {
                val cookie = Cookie("foo", "bar")
                call.response.cookies.append(cookie)

                call.respond("OK")
            }
            get("/FOO") {
                val cookies = call.request.cookies
                if (cookies.rawCookies.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Cookies: ${cookies.rawCookies.entries.joinToString()}")
                } else {
                    call.respond("OK")
                }
            }
            get("/expire") {
                val keys = call.request.cookies.rawCookies.keys
                keys.forEach { name ->
                    call.response.cookies.append(Cookie(name, value = "", maxAge = -1))
                }
                call.respond("OK")
            }
            get("/multiple-comma") {
                val cookies = call.request.cookies
                val first = cookies["fir,st"] ?: error("First not found")
                val second = cookies["sec,ond"] ?: error("Second not found")

                check("first, cookie" == first)
                check("second, cookie" == second)

                with(call.response.cookies) {
                    append(Cookie("third", "third cookie", domain = "127.0.0.1", path = "/"))
                    append(Cookie("fourth", "fourth cookie", domain = "127.0.0.1", path = "/"))
                }
                call.respond("Multiple done")
            }
            get("/encoded") {
                call.respond(call.request.header(HttpHeaders.Cookie) ?: error("Cookie header not found"))
            }
            get("/respond-single-cookie") {
                call.respond(call.request.cookies["single"] ?: error("Cookie single not found"))
            }
            get("/respond-a-minus-b") {
                val a = call.request.cookies["a"]?.toInt() ?: error("Cookie a not found")
                val b = call.request.cookies["b"]?.toInt() ?: error("Cookie b not found")

                call.respond((a - b).toString())
            }
        }
    }
}
