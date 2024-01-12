/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class ApplicationEventTest {
    @Test
    fun testApplicationStopPreparingEvent() {
        var c = 0

        withTestApplication {
            application.monitor.subscribe(ApplicationStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            application.monitor.subscribe(ApplicationStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStoppingEvent() {
        var c = 0

        withTestApplication {
            application.monitor.subscribe(ApplicationStopping) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            application.monitor.subscribe(ApplicationStopping) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStopPreparingEventUsingWithApplication() {
        var c = 0

        withApplication {
            application.monitor.subscribe(ApplicationStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withApplication {
            application.monitor.subscribe(ApplicationStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }
}
