/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.requestvalidation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class RequestValidationTest {

    @Test
    fun testSimpleValidationByClass() = testApplication {
        install(RequestValidation) {
            validate<CharSequence> {
                if (!it.startsWith("+")) {
                    ValidationResult.Invalid(listOf("$it should start with \"+\""))
                } else ValidationResult.Valid
            }
            validate<String> {
                if (!it.endsWith("!")) {
                    ValidationResult.Invalid(listOf("$it should end with \"!\""))
                } else ValidationResult.Valid
            }
        }
        install(StatusPages) {
            exception<RequestValidationException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, "${cause.value}\n${cause.reasons.joinToString()}")
            }
        }
        routing {
            get("/text") {
                val body = call.receive<String>()
                call.respond(body)
            }
            get("/channel") {
                call.receive<ByteReadChannel>().discard()
                call.respond("OK")
            }
        }

        client.get("/text") {
            setBody("1")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals("1\n1 should start with \"+\", 1 should end with \"!\"", body)
        }

        client.get("/text") {
            setBody("+1")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals("+1\n+1 should end with \"!\"", body)
        }

        client.get("/text") {
            setBody("1!")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals("1!\n1! should start with \"+\"", body)
        }

        client.get("/text") {
            setBody("+1!")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("+1!", body)
        }

        client.get("/channel") {
            setBody("1")
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }

    @Test
    fun testValidatorDsl() = testApplication {
        install(RequestValidation) {
            validate {
                filter { it is ByteArray }
                validation {
                    check(it is ByteArray)
                    val intValue = String(it).toInt()
                    if (intValue < 0) {
                        ValidationResult.Invalid("Value is negative")
                    } else ValidationResult.Valid
                }
            }
        }
        install(StatusPages) {
            exception<RequestValidationException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, "${cause.value}\n${cause.reasons.joinToString()}")
            }
        }
        routing {
            get("/text") {
                val body = call.receive<String>()
                call.respond(body)
            }
            get("/array") {
                val body = call.receive<ByteArray>()
                call.respond(String(body))
            }
        }

        client.get("/text") {
            setBody("1")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("1", body)
        }

        client.get("/array") {
            setBody("1")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("1", body)
        }

        client.get("/array") {
            setBody("-1")
        }.let {
            val body = it.bodyAsText()
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertTrue(body.endsWith("Value is negative"), body)
        }
    }
}
