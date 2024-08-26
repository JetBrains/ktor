/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationCallReceiveTest {

    @Test
    fun testReceiveNonNullable() = testApplication {
        val nullPlugin = createApplicationPlugin("NullPlugin") {
            onCallReceive { _ ->
                transformBody {
                    NullBody
                }
            }
        }

        install(nullPlugin)

        routing {
            post("/") {
                val result: String = try {
                    call.receive()
                } catch (cause: Throwable) {
                    cause.message ?: cause.toString()
                }

                call.respond(result)
            }
        }

        val response = client.post("/").bodyAsText()
        try {
            assertEquals("Cannot transform this request's content to kotlin.String", response)
        } catch (cause: Throwable) {
            // on JS/Wasm there is no package name
            assertEquals("Cannot transform this request's content to String", response)
        }
    }
}
