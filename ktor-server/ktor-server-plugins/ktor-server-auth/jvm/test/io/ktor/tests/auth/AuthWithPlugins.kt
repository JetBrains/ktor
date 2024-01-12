/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class AuthWithPlugins {

    @Test
    fun testFormAuthWithJackson() = testApplication {
        install(ContentNegotiation) {
            jackson()
        }
        install(Authentication) {
            form {
                challenge("/unauthorized")
                validate { credentials ->
                    if (credentials.name == credentials.password) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            get("/unauthorized") {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            authenticate {
                post("/test") {
                    call.respondText("OK")
                }
            }
        }

        val response = client.post("/test") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.Found, response.status)

        val location = response.headers[HttpHeaders.Location] ?: fail("Location header is missing")
        assertEquals("/unauthorized", location)
    }
}
