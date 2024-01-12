/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlin.test.*

class TestConnectorInterfaceUsed {
    @Test
    fun testConnectorListenHost() {
        val server = embeddedServer(CIO, applicationEnvironment(), {
            connector {
                host = "some/illegal/host/name"
                port = 9091
            }
        })

        assertFails {
            server.start()

            // this shouldn't happen
            try {
                server.stop(50, 2000)
            } catch (_: Throwable) {
            }
        }
    }
}
