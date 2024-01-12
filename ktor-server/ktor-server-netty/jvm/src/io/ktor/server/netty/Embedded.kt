// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Netty-based [ApplicationEngine]
 */
public object Netty : ApplicationEngineFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration> {

    override fun configuration(
        configure: NettyApplicationEngine.Configuration.() -> Unit
    ): NettyApplicationEngine.Configuration {
        return NettyApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: NettyApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): NettyApplicationEngine {
        return NettyApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}
