/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlin.test.*

class CommandLineConfigTest {
    @Test
    fun testPropertyConfig() {
        setEnvironmentProperty("ktor.deployment.port", "1333")
        val config = CommandLineConfig(emptyArray()).engineConfig
        assertEquals(1333, config.connectors.single().port)
        clearEnvironmentProperty("ktor.deployment.port")
    }

    @Test
    fun testPropertyConfigOverride() {
        setEnvironmentProperty("ktor.deployment.port", "1333")
        val config = CommandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).engineConfig
        assertEquals(13698, config.connectors.single().port)
        clearEnvironmentProperty("ktor.deployment.port")
    }
}
