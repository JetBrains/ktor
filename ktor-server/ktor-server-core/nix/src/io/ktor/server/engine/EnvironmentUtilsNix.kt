/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.config.*
import io.ktor.server.engine.interop.*
import kotlinx.cinterop.*

internal actual fun ApplicationEngine.Configuration.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
) {
    error("SSL is not supported in native")
}

internal actual fun ApplicationEnvironmentBuilder.configurePlatformProperties(args: Array<String>) {}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getConfigFromEnvironment(): ApplicationConfig {
    var index = 0
    val config = MapApplicationConfig()
    val environ = environ ?: return config
    while (environ[index] != null) {
        val env = environ[index]?.toKString() ?: continue
        index++
        if (env.startsWith("ktor.")) {
            val (key, value) = env.splitPair('=') ?: continue
            config.put(key, value)
        }
    }
    return config
}
