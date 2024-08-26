/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.posix.*
import io.ktor.server.engine.interop.environ as interopEnviron

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
internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = buildList {
    var index = 0
    // `platform.posix` also contains `environ` for some targets
    val env = interopEnviron ?: return@buildList
    while (env[index] != null) {
        val keyValue = env[index]?.toKString() ?: continue
        index++
        if (keyValue.startsWith("ktor.")) {
            val (key, value) = keyValue.splitPair('=') ?: continue
            add(key to value)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentProperty(key: String): String? = getenv(key)?.toKString()
