/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

internal expect val CONFIG_PATH: List<String>

/**
 * Loads an application configuration.
 * An implementation of this interface should return [ApplicationConfig] if applicable configuration is found
 * or `null` otherwise.
 */
public interface ConfigLoader {
    /**
     * Tries loading an application configuration from the specified [path].
     *
     * @return configuration or null if the path is not found or configuration format is not supported.
     */
    public fun load(path: String?): ApplicationConfig?

    public companion object {
        /**
         * Find and load a configuration file to [ApplicationConfig].
         */
        public fun load(path: String? = null): ApplicationConfig {
            if (path == null) {
                val default = loadDefault()
                if (default != null) return default
            }

            for (loader in configLoaders) {
                val config = loader.load(path)
                if (config != null) return config
            }

            return MapApplicationConfig()
        }

        private fun loadDefault(): ApplicationConfig? {
            for (defaultPath in CONFIG_PATH) {
                for (loader in configLoaders) {
                    val config = loader.load(defaultPath)
                    if (config != null) return config
                }
            }

            return null
        }
    }
}

/**
 * List of all registered [ConfigLoader] implementations.
 */
public expect val configLoaders: List<ConfigLoader>
