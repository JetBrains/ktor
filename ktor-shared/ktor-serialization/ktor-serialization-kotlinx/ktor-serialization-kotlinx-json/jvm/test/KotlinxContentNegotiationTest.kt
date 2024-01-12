/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class KotlinxContentNegotiationTest : JsonContentNegotiationTest(
    KotlinxSerializationConverter(
        Json {
            ignoreUnknownKeys = true
        }
    )
) {

    @Test
    override fun testRespondNestedSealedWithTypeInfoAny() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        fun buildResponse(): Any = SealedWrapper(Sealed.A("abc"))

        routing {
            get("/") {
                call.respond(buildResponse())
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """{"value":{"type":"io.ktor.client.plugins.contentnegotiation.tests.Sealed.A","value":"abc"}}""",
                response.bodyAsText()
            )
        }
    }
}
