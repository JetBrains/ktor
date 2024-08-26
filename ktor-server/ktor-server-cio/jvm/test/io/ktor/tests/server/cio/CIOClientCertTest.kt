/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class CIOClientCertTest : ClientCertTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    @Test
    override fun `Server requesting Client Certificate from CIO Client`() {
        assertFailsWith<UnsupportedOperationException> {
            super.`Server requesting Client Certificate from CIO Client`()
        }
    }
}
