// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*

/**
 * Engine environment configuration builder
 */
@KtorDsl
public actual class ApplicationEnvironmentBuilder {

    /**
     * Application logger
     */
    public actual var log: Logger = KtorSimpleLogger("io.ktor.server.Application")

    /**
     * Application config
     */
    public actual var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Build an application engine environment
     */
    public actual fun build(): ApplicationEnvironment {
        return ApplicationEnvironmentImplNix(log, config)
    }
}

public class ApplicationEnvironmentImplNix(
    override val log: Logger,
    override val config: ApplicationConfig,
    @Deprecated(
        "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING
    )
    override val monitor: Events = Events()
) : ApplicationEnvironment
