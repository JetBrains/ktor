/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty.http2

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

//class JettyHttp2AsyncServletContainerCompressionTest :
//    CompressionTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
//
//class JettyHttp2AsyncServletContainerContentTest :
//    ContentTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
//
//class JettyHttp2AsyncServletContainerHttpServerCommonTest :
//    HttpServerCommonTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
//        Servlet(async = true)
//    ) {
//    override fun testFlushingHeaders() {
//        // no op
//    }
//}
//
//class JettyHttp2AsyncServletContainerHttpServerJvmTest :
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
//class JettyHttp2AsyncServletContainerSustainabilityTest :
//    SustainabilityTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))
