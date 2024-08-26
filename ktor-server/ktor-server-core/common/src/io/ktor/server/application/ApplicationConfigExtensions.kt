/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.config.*

/**
 * The port the current application is running on, as defined by the configuration at start-up.
 **/
public val ApplicationConfig.port: Int get() = propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080

/**
 * The host address of the currently running application, as defined by the configuration at start-up.
 **/
public val ApplicationConfig.host: String get() = propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
