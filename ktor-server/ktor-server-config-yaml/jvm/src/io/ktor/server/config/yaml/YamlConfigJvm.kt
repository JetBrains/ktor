/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import io.ktor.server.config.*
import net.mamoe.yamlkt.*
import java.io.*

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exist; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 */
public actual fun YamlConfig(path: String?): YamlConfig? {
    val resolvedPath = when {
        path == null -> DEFAULT_YAML_FILENAME
        path.endsWith(".yaml") -> path
        else -> return null
    }
    val resource = Thread.currentThread().contextClassLoader.getResource(resolvedPath)
    if (resource != null) {
        return resource.openStream().use {
            configFromString(String(it.readBytes()))
        }
    }
    val file = File(resolvedPath)
    if (file.exists()) {
        return configFromString(file.readText())
    }
    return null
}

private fun configFromString(content: String): YamlConfig {
    val yaml = Yaml.decodeYamlFromString(content) as? YamlMap
        ?: throw ApplicationConfigurationException("Config should be a YAML dictionary")

    return YamlConfig(yaml).apply { checkEnvironmentVariables() }
}

internal actual fun getEnvironmentValue(key: String): String? = System.getenv(key)
