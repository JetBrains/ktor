/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.tomcat.jakarta

import io.ktor.junit.*
import io.ktor.server.testing.suites.*
import io.ktor.server.tomcat.jakarta.*
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*

@ExtendWith(RetrySupport::class)
class TomcatWebSocketTest :
    WebSocketEngineSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {

    @Ignore
    override fun testClientClosingFirst() {
    }

    @Ignore
    override fun testFragmentedFlagsFromTheFirstFrame() {
    }
}
