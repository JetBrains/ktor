/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.engine.*
import java.util.*

internal actual val CONFIG_PATH: List<String>
    get() = listOfNotNull(
        getEnvironmentProperty("config.file"),
        getEnvironmentProperty("config.resource"),
        getEnvironmentProperty("config.url"),
    )

public actual val configLoaders: List<ConfigLoader> = ConfigLoader::class.java.let {
    ServiceLoader.load(it, it.classLoader).toList()
}
