/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.servlet

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.servlet.*
import io.ktor.server.servlet.ServletApplicationEngine.Companion.ApplicationAttributeKey
import io.ktor.server.servlet.ServletApplicationEngine.Companion.ApplicationEnginePipelineAttributeKey
import io.ktor.server.servlet.ServletApplicationEngine.Companion.EnvironmentAttributeKey
import io.mockk.*
import java.util.*
import javax.servlet.*
import javax.servlet.http.*
import kotlin.test.*

class ConfigTest {
    @Test
    fun resolveParametersFromCustomConfig() {
        val engine = ServletApplicationEngine()
        val pipeline = EnginePipeline()

        var interceptorCalled = false
        pipeline.intercept(EnginePipeline.Call) {
            val value = call.application.environment.config.property("var").getString()
            assertEquals("test", value)
            interceptorCalled = true
        }

        val context = mockk<ServletContext> {
            every { getAttribute(ApplicationEnginePipelineAttributeKey) } returns pipeline
            every { initParameterNames } returns Collections.enumeration(listOf("io.ktor.ktor.config"))
            every { classLoader } returns this::class.java.classLoader
            every { contextPath } returns "/"
            every { getAttribute(ApplicationAttributeKey) } returns null
            every { getAttribute(EnvironmentAttributeKey) } returns null
            every { serverInfo } returns ""
        }

        val config = mockk<ServletConfig> {
            every { getInitParameter("io.ktor.ktor.config") } returns "test.conf"
            every { servletContext } returns context
            every { servletName } returns "ktor-test"
            every { initParameterNames } returns Collections.enumeration(emptyList())
        }

        engine.init(config)
        engine.service(getRequest(), getResponse())
        engine.destroy()
        assertTrue(interceptorCalled)
    }

    @Test
    fun resolveYamlFromCustomConfig() {
        val engine = ServletApplicationEngine()
        val pipeline = EnginePipeline()

        var interceptorCalled = false
        pipeline.intercept(EnginePipeline.Call) {
            val value = call.application.environment.config.property("property").getString()
            assertEquals("a-custom", value)
            interceptorCalled = true
        }

        val context = mockk<ServletContext> {
            every { getAttribute(ApplicationEnginePipelineAttributeKey) } returns pipeline
            every { initParameterNames } returns Collections.enumeration(listOf("io.ktor.ktor.config"))
            every { classLoader } returns this::class.java.classLoader
            every { contextPath } returns "/"
            every { getAttribute(ApplicationAttributeKey) } returns null
            every { getAttribute(EnvironmentAttributeKey) } returns null
            every { serverInfo } returns ""
        }

        val config = mockk<ServletConfig> {
            every { getInitParameter("io.ktor.ktor.config") } returns "custom-config.yaml"
            every { servletContext } returns context
            every { servletName } returns "ktor-test"
            every { initParameterNames } returns Collections.enumeration(emptyList())
        }

        engine.init(config)
        engine.service(getRequest(), getResponse())
        engine.destroy()
        assertTrue(interceptorCalled)
    }

    private fun getResponse(): HttpServletResponse {
        val error = slot<String>()
        return mockk {
            every { sendError(500, capture(error)) } answers {
                fail(error.captured)
            }
            every { isCommitted } returns false
        }
    }

    private fun getRequest(): HttpServletRequest {
        return mockk {
            every { isAsyncSupported } returns false
            every { queryString } returns ""
            every { requestURI } returns "/"
            every { protocol } returns "HTTP/1.1"
            every { scheme } returns "http"
            every { method } returns "GET"
            every { serverPort } returns 80
            every { serverName } returns "server"
            every { remoteHost } returns "localhost"
            every { attributeNames } returns Collections.enumeration(emptyList())
        }
    }
}
