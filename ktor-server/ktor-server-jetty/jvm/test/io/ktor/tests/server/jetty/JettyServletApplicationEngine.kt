/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.servlet.*
import org.eclipse.jetty.servlet.*
import javax.servlet.*

/**
 * The factory and engine are only suitable for testing. You shouldn't use it for production code.
 */
internal class Servlet(
    private val async: Boolean
) : ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun configuration(
        configure: JettyApplicationEngineBase.Configuration.() -> Unit
    ): JettyApplicationEngineBase.Configuration {
        return JettyApplicationEngineBase.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: JettyApplicationEngineBase.Configuration,
        applicationProvider: () -> Application
    ): JettyServletApplicationEngine {
        return JettyServletApplicationEngine(
            environment,
            monitor,
            developmentMode,
            configuration,
            applicationProvider,
            async
        )
    }
}

internal class JettyServletApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    applicationProvider: () -> Application,
    async: Boolean
) : JettyApplicationEngineBase(environment, monitor, developmentMode, configuration, applicationProvider) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.EnvironmentAttributeKey, environment)
            setAttribute(ServletApplicationEngine.ApplicationAttributeKey, applicationProvider)
            setAttribute(ServletApplicationEngine.ApplicationEnginePipelineAttributeKey, pipeline)

            insertHandler(
                ServletHandler().apply {
                    val holder = ServletHolder(
                        "ktor-servlet",
                        ServletApplicationEngine::class.java
                    ).apply {
                        isAsyncSupported = async
                        registration.setLoadOnStartup(1)
                        registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
                        registration.setAsyncSupported(async)
                    }

                    addServlet(holder)
                    addServletMapping(
                        ServletMapping().apply {
                            pathSpecs = arrayOf("*.", "/*")
                            servletName = "ktor-servlet"
                        }
                    )
                }
            )
        }
    }
}
