/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import io.ktor.server.config.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.test.*

class YamlConfigTestNix {

    @Test
    fun testLoadWithoutConfig() {
        val config = YamlConfig(null)
        assertNull(config)
    }

    @Test
    fun testLoadDefaultConfig() {
        val content = """
            ktor:
                deployment:
                    port: 1234
                auth:
                    users:
                        - a
                        - b
                        - c
        """.trimIndent()
        val config = withFile("application.yaml", content) {
            YamlConfig(null)!!
        }
        assertEquals("1234", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("a", "b", "c"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadCustomConfig() {
        val content = """
            ktor:
                deployment:
                    port: 2345
                auth:
                    users:
                        - c
                        - d
                        - e
        """.trimIndent()
        val config = withFile("application-custom.yaml", content) {
            YamlConfig("application-custom.yaml")!!
        }
        assertEquals("2345", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("c", "d", "e"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadWrongConfig() {
        val content = """
            ktor:
                deployment:
                    port: ${'$'}NON_EXISTING_VARIABLE
        """.trimIndent()
        withFile("application-no-env.yaml", content) {
            assertFailsWith<ApplicationConfigurationException> {
                YamlConfig("application-no-env.yaml")!!
            }
        }
    }

    private fun <T> withFile(path: String, content: String, block: () -> T): T {
        writeFile(path, content)
        val result = block()
        remove(path)
        return result
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    private fun writeFile(path: String, content: String) {
        val file = fopen(path, "w")
        val bytes = content.toByteArray()
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }
        fclose(file)
    }
}
