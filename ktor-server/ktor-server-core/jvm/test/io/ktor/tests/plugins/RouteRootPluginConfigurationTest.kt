/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.application.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.testing.*
import kotlin.test.*

class RouteRootPluginConfigurationTest {

    @Test
    fun testReadPropertyFromFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        environment {
            config = load("test-config.yaml")
        }

        install(plugin)

        startApplication()
        assertEquals("42", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithoutConfigFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        install(plugin)

        startApplication()
        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithEmptyConfigFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        install(plugin)

        environment {
            config = load("empty-config.yaml")
        }

        startApplication()
        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testComplexPath() = testApplication {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "db.myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        environment {
            config = load("test-config.yaml")
        }

        install(plugin)

        startApplication()
        assertEquals("43", lastInstalledValue)
    }
}
