/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.test.*

@Suppress("DEPRECATION")
class RespondWriteTest {
    @Test
    fun smoke() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondTextWriter { write("OK") }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testFailureInside() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondTextWriter {
                        throw IllegalStateException("expected")
                    }
                }
            }

            assertFailsWith<IllegalStateException> {
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }

    @Test
    fun testSuspendInside() {
        withTestApplication {
            val executor = Executors.newSingleThreadExecutor()
            application.monitor.subscribe(ApplicationStopped) { executor.shutdown() }
            application.routing {
                get("/") {
                    call.respondTextWriter {
                        withContext(executor.asCoroutineDispatcher()) {
                            write("OK")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("OK", call.response.content)
            }
        }
    }

    //    @Test
    @Suppress("UNUSED")
    fun testFailureInsideUnresolvedCase() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondTextWriter {
                        close() // after that point the main pipeline is going to continue since the channel is closed
                        // so we can't simply bypass an exception
                        // the workaround is to hold pipeline on channel pipe until commit rather than just close

                        Thread.sleep(1000)
                        throw IllegalStateException("expected")
                    }
                }
            }

            assertFailsWith<IllegalStateException> {
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }
}
