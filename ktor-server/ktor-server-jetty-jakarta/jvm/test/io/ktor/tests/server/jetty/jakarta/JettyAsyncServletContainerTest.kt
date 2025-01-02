/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

//class JettyAsyncServletContainerCompressionTest :
//    CompressionTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
//
//class JettyAsyncServletContainerContentTest :
//    ContentTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
//
//class JettyAsyncServletContainerHttpServerCommonTest :
//    HttpServerCommonTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = true)
//    ) {
//    override fun testFlushingHeaders() {
//        // no op
//    }
//}
//
//class JettyAsyncServletContainerHttpServerJvmTest :
//    HttpServerJvmTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = true)
//    ) {
//    @Ignore
//    override fun testPipelining() {
//    }
//
//    @Ignore
//    override fun testPipeliningWithFlushingHeaders() {
//    }
//}
//
//class JettyAsyncServletContainerSustainabilityTest :
//    SustainabilityTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
//
//class JettyAsyncServerPluginsTest :
//    ServerPluginsTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = true)
//    ) {
//    init {
//        enableHttp2 = false
//        enableSsl = false
//    }
//}
