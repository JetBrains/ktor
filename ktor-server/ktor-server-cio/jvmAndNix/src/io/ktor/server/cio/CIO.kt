/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
public object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {

    override fun configuration(
        configure: CIOApplicationEngine.Configuration.() -> Unit
    ): CIOApplicationEngine.Configuration {
        return CIOApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: CIOApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): CIOApplicationEngine {
        return CIOApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}
