/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import test.server.*

internal fun Application.contentTestServer() {
    routing {
        route("/content") {
            get("/uri") {
                call.respondText { call.request.local.uri }
            }

            get("/empty") {
                call.respond("")
            }
            get("/chunked-data") {
                call.respondTextWriter {
                    val text = "x".repeat(call.parameters["size"]?.toInt() ?: 1000)
                    text.chunked(255).forEach { write(it) }
                }
            }
            head("/emptyHead") {
                call.respond(
                    object : OutgoingContent.NoContent() {
                        override val contentLength: Long = 150
                    }
                )
            }
            get("/hello") {
                call.respond("hello")
            }
            get("/xxx") {
                call.respond(
                    buildString {
                        append("x".repeat(100))
                    }
                )
            }
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                call.respond(content)
            }
            get("/news") {
                val form = call.request.queryParameters

                check("myuser" == form["user"]!!)
                check("10" == form["page"]!!)

                call.respond("100")
            }
            post("/sign") {
                val form = call.receiveParameters()

                check("myuser" == form["user"]!!)
                check("abcdefg" == form["token"]!!)

                call.respond("success")
            }
            post("/upload") {
                val response = StringBuilder()
                call.receiveMultipart().forEachPart { part ->
                    check(part.contentDisposition?.disposition == "form-data")
                    response.append(part.makeString())
                    part.dispose()
                }

                call.respondText(response.toString())
            }
            put("/file-upload") {
                val parts = call.receiveMultipart().readAllParts()
                if (call.request.headers[HttpHeaders.ContentLength] == null) error("Content length is missing")

                if (parts.size != 1) call.fail("Invalid form size: $parts")

                val file = parts.first() as? PartData.FileItem ?: call.fail("Invalid item")

                if (4 != file.headers[HttpHeaders.ContentLength]?.toInt()) call.fail("Size is missing")

                val value = file.provider().readInt()
                if (value != 42) call.fail("Invalid content")

                call.respond(HttpStatusCode.OK)
            }
            get("/stream") {
                val delay = call.parameters["delay"]?.toLong() ?: 0L
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            while (true) {
                                channel.writeInt(42)
                                channel.flush()
                                delay(delay)
                            }
                        }
                    }
                )
            }
        }
    }
}
