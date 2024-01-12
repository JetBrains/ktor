/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import com.typesafe.config.*
import io.ktor.server.config.*
import io.ktor.server.netty.*
import io.ktor.server.netty.EngineMain.loadConfiguration
import io.netty.handler.codec.http.*
import kotlin.test.*

class EngineMainTest {

    @Test
    fun testNettyCodecConfiguration() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                    ktor {
                        deployment {
                            maxInitialLineLength: 2048,
                            maxHeaderSize: 1024,
                            maxChunkSize: 42
                        }
                    }
                """.trimIndent()
            )
        )

        val configuration = NettyApplicationEngine.Configuration().apply { loadConfiguration(config) }

        assertEquals(2048, configuration.maxInitialLineLength)
        assertEquals(1024, configuration.maxHeaderSize)
        assertEquals(42, configuration.maxChunkSize)
    }

    @Test
    fun testNettyCodecDefaultConfiguration() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                    ktor {
                        deployment {
                        }
                    }
                """.trimIndent()
            )
        )

        val configuration = NettyApplicationEngine.Configuration().apply { loadConfiguration(config) }

        assertEquals(HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, configuration.maxInitialLineLength)
        assertEquals(HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE, configuration.maxHeaderSize)
        assertEquals(HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE, configuration.maxChunkSize)
    }
}
