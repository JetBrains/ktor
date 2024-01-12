/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.coroutines.*
import kotlin.test.*

suspend fun waitForCondition(
    description: String? = null,
    waitIncrement: Long = 200L,
    waitLimit: Long = 10_000L,
    condition: () -> Boolean,
) {
    var waitTime = 0L
    while (waitTime < waitLimit) {
        if (condition()) {
            return
        }
        delay(waitIncrement)
        waitTime += waitIncrement
    }
    assertTrue(condition(), "Timed out after ${waitLimit / 1000}s waiting for ${description ?: condition}")
}
