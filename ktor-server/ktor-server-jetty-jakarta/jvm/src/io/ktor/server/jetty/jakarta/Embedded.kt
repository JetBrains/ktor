// ktlint-disable filename
/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Jetty-based [ApplicationEngine]
 */
public object Jetty : ApplicationEngineFactory<JettyApplicationEngine, JettyApplicationEngineBase.Configuration> {

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
    ): JettyApplicationEngine {
        return JettyApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}
