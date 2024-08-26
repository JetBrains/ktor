/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class AutoHeadResponseJvmTest {

    @Test
    fun testTextRespond() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.respondTextWriter {
                        write("Hello")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello", call.response.content)
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    private fun withHeadApplication(block: TestApplicationEngine.() -> Unit) {
        withTestApplication {
            application.install(AutoHeadResponse)
            block()
        }
    }
}
