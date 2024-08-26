/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import java.nio.file.*
import kotlin.io.path.*
import kotlin.test.*

class ConfigJvmTest {

    @Test
    fun testLoadFromResources() {
        System.clearProperty("config.file")
        System.setProperty("config.resource", "custom.config.conf")
        val config = ConfigLoader.load()

        assertEquals(4242, config.port)
    }

    @Test
    fun testLoadYamlFromResources() {
        System.clearProperty("config.file")
        System.setProperty("config.resource", "custom.config.yaml")
        val config = ConfigLoader.load()

        assertEquals(4244, config.port)
    }

    @Test
    fun testLoadFromFile() {
        val file = Files.createTempFile("ConfigJvmTest", "my.config.conf")
        file.writeText(
            """
            ktor {
                deployment {
                    port = 4243
                }
            }
            """.trimIndent()
        )

        System.clearProperty("config.resource")
        System.setProperty("config.file", file.absolutePathString())
        val config = ConfigLoader.load()

        assertEquals(4243, config.port)
    }
}
