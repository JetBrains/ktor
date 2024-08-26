/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.jsonTest() {
    routing {
        route("json") {
            get("user-generic") {
                call.respondText(
                    """
                    {
                        "message": "ok",
                        "data": { "name": "hello" }
                    }
                    """.trimIndent(),
                    contentType = ContentType.Application.Json
                )
            }
        }
    }
}
