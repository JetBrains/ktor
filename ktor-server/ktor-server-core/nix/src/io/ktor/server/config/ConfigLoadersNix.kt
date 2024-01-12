/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.utils.io.*
import kotlinx.cinterop.*
import platform.posix.*

internal actual val CONFIG_PATH: List<String> get() = buildList {
    fromEnv("CONFIG_FILE")?.let { add(it) }
}

@OptIn(ExperimentalForeignApi::class)
private fun fromEnv(name: String): String? = getenv(name)?.toKString()

/**
 * List of all registered [ConfigLoader] implementations.
 */
public actual val configLoaders: List<ConfigLoader>
    get() = _configLoaders

private val _configLoaders: MutableList<ConfigLoader> = mutableListOf()

@OptIn(InternalAPI::class)
public fun addConfigLoader(loader: ConfigLoader) {
    _configLoaders.add(loader)
}
