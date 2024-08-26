/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty.jakarta

import io.ktor.client.statement.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.jakarta.*
import io.ktor.server.testing.suites.*
import jakarta.servlet.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.util.component.*
import kotlin.test.*

class JettyCompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyJakartaContentTest : ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
)

class JettyHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyHttpServerJvmTest : HttpServerJvmTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        super.configure(configuration)
        configuration.configureServer = {
            addAttributesHandler()
        }
    }

    @Test
    fun testServletAttributes() = runTest {
        createAndStartServer {
            get("/tomcat/attributes") {
                call.respondText(
                    call.request.servletRequestAttributes["ktor.test.attribute"]?.toString() ?: "Not found"
                )
            }
        }

        withUrl("/tomcat/attributes", {}) {
            assertEquals("135", call.response.bodyAsText())
        }
    }

    private fun Server.addAttributesHandler() {
        addEventListener(
            object : LifeCycle.Listener {
                override fun lifeCycleStarting(event: LifeCycle?) {
                    super.lifeCycleStarting(event)
                    val delegate = handler
                    handler = object : DefaultHandler() {
                        override fun handle(
                            target: String?,
                            baseRequest: Request?,
                            request: HttpServletRequest?,
                            response: HttpServletResponse?
                        ) {
                            request?.setAttribute("ktor.test.attribute", "135")
                            delegate?.handle(target, baseRequest, request, response)
                        }
                    }
                }
            }
        )
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettySustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyConfigTest : ConfigTestSuite(Jetty)

class JettyConnectionTest : ConnectionTestSuite(Jetty)

class JettyServerPluginsTest : ServerPluginsTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
