/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationPluginConfigurationTest {

    @Test
    fun testReadPropertyFromFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createApplicationPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        environment {
            config = ConfigLoader.load("test-config.yaml")
        }

        install(plugin)
        startApplication()
        assertEquals("42", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithoutConfigFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createApplicationPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        install(plugin)
        startApplication()
        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithEmptyConfigFile() = testApplication {
        var lastInstalledValue = ""
        val plugin = createApplicationPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        install(plugin)

        environment {
            config = ConfigLoader.load("empty-config.yaml")
        }

        startApplication()
        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testComplexPath() = testApplication {
        var lastInstalledValue = ""
        val plugin = createApplicationPlugin("PluginWithProperty", "db.myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        environment {
            config = ConfigLoader.load("test-config.yaml")
        }

        install(plugin)

        startApplication()
        assertEquals("43", lastInstalledValue)
    }
}
