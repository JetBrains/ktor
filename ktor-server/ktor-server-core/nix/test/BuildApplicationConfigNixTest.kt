/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.engine.*
import platform.posix.*
import kotlin.test.*

class BuildApplicationConfigNixTest {

    @Test
    fun testPropertyConfig() {
        setenv("ktor.deployment.port", "1333", 0)
        val config = CommandLineConfig(emptyArray()).engineConfig
        assertEquals(1333, config.connectors.single().port)
        unsetenv("ktor.deployment.port")
    }

    @Test
    fun testPropertyConfigOverride() {
        setenv("ktor.deployment.port", "1333", 0)
        val config = CommandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).engineConfig
        assertEquals(13698, config.connectors.single().port)
        unsetenv("ktor.deployment.port")
    }
}
