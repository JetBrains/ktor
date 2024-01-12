// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

public actual interface ApplicationEnvironment {

    /**
     * Configuration for the [Application]
     */
    public actual val config: ApplicationConfig

    /**
     * Instance of [Logger] to be used for logging.
     */
    public actual val log: Logger
}

internal actual class ApplicationPropertiesBridge actual constructor(
    applicationProperties: ApplicationProperties,
    internal actual val parentCoroutineContext: CoroutineContext,
)
