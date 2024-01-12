/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.logging.*
import kotlin.test.*

class KtorSimpleLoggerTest {

    @Test
    fun testLogLevelDefault() {
        if (!PlatformUtils.IS_NODE) return

        val logger = KtorSimpleLogger("test")
        val level = logger.level
        assertEquals(LogLevel.INFO, level)
    }

    @Test
    fun testSetLogLevel() {
        if (!PlatformUtils.IS_NODE) return

        js("process.env['KTOR_LOG_LEVEL'] = 'WARN'")

        val logger = KtorSimpleLogger("test")
        val level = logger.level
        assertEquals(LogLevel.WARN, level)
    }
}
