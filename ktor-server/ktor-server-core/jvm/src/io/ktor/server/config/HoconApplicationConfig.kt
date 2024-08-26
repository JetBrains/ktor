/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import com.typesafe.config.*
import java.io.*

/**
 * Loads a [Config] from a hocon file.
 */
public class HoconConfigLoader : ConfigLoader {

    /**
     * Tries loading an application configuration from the specified [path].
     *
     * @return configuration or null if the path is not found or configuration format is not supported.
     */
    override fun load(path: String?): ApplicationConfig? {
        val resolvedPath = when {
            path == null -> "application.conf"
            path.endsWith(".conf") || path.endsWith(".json") || path.endsWith(".properties") -> path
            else -> return null
        }

        val resource = Thread.currentThread().contextClassLoader.getResource(resolvedPath)
        val config = when {
            resource != null -> ConfigFactory.load(resolvedPath)
            else -> {
                val file = File(resolvedPath)
                if (file.exists()) ConfigFactory.parseFile(file) else null
            }
        }?.resolve() ?: return null

        return HoconApplicationConfig(config)
    }
}

/**
 * Implements [ApplicationConfig] by loading configuration from HOCON data structures
 */
public open class HoconApplicationConfig(private val config: Config) : ApplicationConfig {
    override fun property(path: String): ApplicationConfigValue {
        if (!config.hasPath(path)) {
            throw ApplicationConfigurationException("Property $path not found.")
        }
        return HoconApplicationConfigValue(config, path)
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        if (!config.hasPath(path)) {
            return null
        }
        return HoconApplicationConfigValue(config, path)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        return config.getConfigList(path).map { HoconApplicationConfig(it) }
    }

    override fun config(path: String): ApplicationConfig = HoconApplicationConfig(config.getConfig(path))

    override fun keys(): Set<String> {
        return config.entrySet().map { it.key }.toSet()
    }

    override fun toMap(): Map<String, Any?> {
        return config.root().unwrapped()
    }

    private class HoconApplicationConfigValue(val config: Config, val path: String) : ApplicationConfigValue {
        override fun getString(): String = config.getString(path)
        override fun getList(): List<String> = config.getStringList(path)
    }
}

/**
 * Returns a string value for [path] or `null` if missing
 */
public fun Config.tryGetString(path: String): String? = if (hasPath(path)) getString(path) else null

/**
 * Returns a list of values for [path] or `null` if missing
 */
public fun Config.tryGetStringList(path: String): List<String>? = if (hasPath(path)) getStringList(path) else null

/**
 * Returns [ApplicationConfig] by loading configuration from a resource specified by [configPath]
 * or a default resource if [configPath] is `null`
 */
public fun ApplicationConfig(configPath: String?): ApplicationConfig =
    ConfigLoader.load(configPath)
