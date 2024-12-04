// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Jetty-based [ApplicationEngine]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.jetty.Jetty)
 */
@Deprecated(
    "The ktor-server-jetty module is deprecated and will be removed in the next major release as it " +
        "references an outdated version of Jetty. Please use the ktor-server-jetty-jakarta module instead."
)
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
