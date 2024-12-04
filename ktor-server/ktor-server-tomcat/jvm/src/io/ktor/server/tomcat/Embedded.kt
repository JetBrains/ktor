// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Tomcat-based [ApplicationEngine]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.tomcat.Tomcat)
 */
@Deprecated(
    "The ktor-server-tomcat module is deprecated and will be removed in the next major release as it " +
        "references an outdated version of Tomcat. Please use the ktor-server-tomcat-jakarta module instead."
)
public object Tomcat : ApplicationEngineFactory<TomcatApplicationEngine, TomcatApplicationEngine.Configuration> {

    override fun configuration(
        configure: TomcatApplicationEngine.Configuration.() -> Unit
    ): TomcatApplicationEngine.Configuration {
        return TomcatApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TomcatApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): TomcatApplicationEngine {
        return TomcatApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}
