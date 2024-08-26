// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Application] runs
 */
public expect interface ApplicationEnvironment {

    /**
     * Instance of [Logger] to be used for logging.
     */
    public val log: Logger

    /**
     * Configuration for the [Application]
     */
    public val config: ApplicationConfig

    /**
     * Provides events on Application lifecycle
     */
    @Deprecated(
        message = "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.ERROR,
    )
    public val monitor: Events
}

internal expect class ApplicationPropertiesBridge(
    applicationProperties: ApplicationProperties,
    parentCoroutineContext: CoroutineContext
) {
    internal val parentCoroutineContext: CoroutineContext
}
