/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.headersTestServer() {
    routing {
        route("/headers") {
            get {
                call.response.header("X-Header-Single-Value", "foo")
                call.response.header("X-Header-Double-Value", "foo")
                call.response.header("X-Header-Double-Value", "bar")
                val message = call.request.header(HttpHeaders.ContentLength) ?: ""
                call.respond(HttpStatusCode.OK, message)
            }
            post {
                val message = call.request.header(HttpHeaders.ContentLength) ?: ""
                call.respond(HttpStatusCode.OK, message)
            }
            put {
                val message = call.request.header(HttpHeaders.ContentLength) ?: ""
                call.respond(HttpStatusCode.OK, message)
            }
            head {
                val message = call.request.header(HttpHeaders.ContentLength) ?: ""
                call.respond(HttpStatusCode.OK, message)
            }
            get("host") {
                val header = call.request.headers.getAll(HttpHeaders.Host)

                if (header.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Header is or empty: ${header?.size}")
                    return@get
                }

                if (header.size > 1) {
                    call.respond(HttpStatusCode.BadRequest, "Too many host headers: ${header.joinToString()}")
                    return@get
                }

                if (header.first() != "CustomHost") {
                    call.respond(HttpStatusCode.BadRequest, "Invalid host header: ${header.first()}")
                    return@get
                }

                call.respond(HttpStatusCode.OK)
            }

            get("/echo") {
                val headerName = call.request.queryParameters["headerName"] ?: ""
                call.respondText(call.request.headers[headerName] ?: "no header")
            }
        }

        route("/headers-merge") {
            accept(ContentType.Application.Json) {
                get {
                    call.respondText("JSON", ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
            accept(ContentType.Application.Xml) {
                get {
                    call.respondText("XML", ContentType.Application.Xml, HttpStatusCode.OK)
                }
            }
        }

        route("/content-type") {
            post {
                val contentType = call.request.headers[HttpHeaders.ContentType]
                call.respondText(contentType ?: "")
            }
        }
    }
}
