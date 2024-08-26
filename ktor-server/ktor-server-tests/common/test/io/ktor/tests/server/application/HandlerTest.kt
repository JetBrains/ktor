/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlin.test.*

class HandlerTest {

    @Test
    fun application_with_empty_handler() = testApplication {
        on("making a request") {
            val call = client.get { }
            it("should not be handled") {
                assertFalse(call.status.isSuccess())
            }
        }
    }

    @Test
    fun application_with_transparent_handler() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {}
        }
        on("making a request") {
            val call = client.get { }
            it("should not be handled") {
                assertFalse(call.status.isSuccess())
            }
        }
    }

    @Test
    fun application_with_handler_returning_true() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) { call.respond(HttpStatusCode.OK) }
        }
        on("making a request") {
            val call = client.get { }
            it("should be handled") {
                assertTrue(call.status.isSuccess())
            }
        }
    }

    @Test
    fun application_with_handler_that_checks_body_on_POST_method() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                if (call.request.httpMethod == HttpMethod.Post) {
                    assertEquals("Body", call.receive())
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
        val call = client.post {
            setBody("Body")
        }
        assertTrue(call.status.isSuccess())
    }

    @Test
    fun application_with_handler_that_returns_true_on_POST_method() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                if (call.request.httpMethod == HttpMethod.Post) {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
        on("making a GET request") {
            val call = client.get {}
            it("should not be handled") {
                assertFalse(call.status.isSuccess())
            }
        }
        on("making a POST request") {
            val call = client.post { }
            it("should be handled") {
                assertTrue(call.status.isSuccess())
            }
        }
    }
}
