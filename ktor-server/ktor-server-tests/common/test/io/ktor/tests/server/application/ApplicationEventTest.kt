/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ApplicationEventTest {
    @Test
    fun testApplicationStopPreparingEvent() = runTest {
        var c = 0

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopPreparing) {
                    c += 1
                }
            }
        }
        assertEquals(1, c)

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopPreparing) {
                    c += 2
                }
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStoppingEvent() = runTest {
        var c = 0

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopping) {
                    c += 1
                }
            }
        }
        assertEquals(1, c)

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopping) {
                    c += 2
                }
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStopPreparingEventUsingWithApplication() = runTest {
        var c = 0

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopPreparing) {
                    c += 1
                }
            }
        }
        assertEquals(1, c)

        runTestApplication {
            application {
                monitor.subscribe(ApplicationStopPreparing) {
                    c += 2
                }
            }
        }
        assertEquals(3, c)
    }
}
