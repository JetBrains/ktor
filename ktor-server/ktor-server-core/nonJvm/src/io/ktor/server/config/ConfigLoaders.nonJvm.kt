/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.engine.*

internal actual val CONFIG_PATH: List<String>
    get() = listOfNotNull(
        getEnvironmentProperty("CONFIG_FILE")
    )

/**
 * List of all registered [ConfigLoader] implementations.
 */
public actual val configLoaders: List<ConfigLoader>
    get() = _configLoaders

@Suppress("ObjectPropertyName")
private val _configLoaders: MutableList<ConfigLoader> = mutableListOf()

public fun addConfigLoader(loader: ConfigLoader) {
    _configLoaders.add(loader)
}
