/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

//class JettyBlockingServletContainerCompressionTest :
//    CompressionTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false))
//
//class JettyBlockingServletContainerContentTest :
//    ContentTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false))
//
//class JettyBlockingServletContainerHttpServerCommonTest :
//    HttpServerCommonTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = false)
//    ) {
//    override fun testFlushingHeaders() {
//        // no op
//    }
//}
//
//class JettyBlockingServletContainerHttpServerJvmTest :
//    HttpServerJvmTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = false)
//    ) {
//
//    @Ignore
//    override fun testUpgrade() {
//    }
//
//    @Ignore
//    override fun testPipelining() {
//    }
//
//    @Ignore
//    override fun testPipeliningWithFlushingHeaders() {
//    }
//}
//
//class JettyBlockingServletContainerSustainabilityTest :
//    SustainabilityTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = false)
//    )
//
//class JettyBlockingServletServerPluginTest :
//    ServerPluginsTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = false)
//    ) {
//    init {
//        enableHttp2 = false
//        enableSsl = false
//    }
//}
