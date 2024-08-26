/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

internal fun Application.encodingTestServer() {
    routing {
        route("/compression") {
            route("/deflate") {
                install(Compression) {
                    deflate()
                    minimumSize(0)
                }
                setCompressionEndpoints()
            }
            route("/gzip") {
                install(Compression) {
                    gzip()
                    minimumSize(0)
                }
                setCompressionEndpoints()
            }
            route("/gzip-large") {
                install(Compression) {
                    gzip()
                }
                get {
                    call.respond(ByteArray(500) { it.toByte() })
                }
            }
            route("/gzip-precompressed") {
                get {
                    val channel = ByteReadChannel(ByteArray(500) { it.toByte() })
                    call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override fun readFrom(): ByteReadChannel = GZipEncoder.encode(channel)
                            override val contentLength: Long = 294
                        }
                    )
                }
            }
            route("/gzip-empty") {
                get {
                    call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
                    call.respondText("")
                }
            }
            route("/identity") {
                install(Compression) {
                    identity()
                    minimumSize(0)
                }
                setCompressionEndpoints()
            }
            route("/big-plain-text") {
                get {
                    call.respondText {
                        buildString {
                            for (i in 1..10_000)
                                appendLine("I will not introduce deadlocks.")
                        }
                    }
                }
            }
        }
    }
}

private fun Route.setCompressionEndpoints() {
    get {
        call.respondText("Compressed response!")
    }
}
