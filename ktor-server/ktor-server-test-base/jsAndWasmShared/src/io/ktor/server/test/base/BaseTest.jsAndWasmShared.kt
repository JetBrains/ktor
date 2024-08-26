/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@Suppress("FunctionName")
actual abstract class BaseTest actual constructor() {
    actual open val timeout: Duration = 10.seconds

    private val errors = mutableListOf<Throwable>()

    actual fun collectUnhandledException(error: Throwable) {
        errors.add(error)
    }

    @AfterTest
    fun _verifyErrors() {
        if (errors.isEmpty()) return

        val error = UnhandledErrorsException(
            "There were ${errors.size} unhandled errors during running test (suppressed)"
        )

        errors.forEach {
            error.addSuppressed(it)
        }
        error.printStackTrace()
        throw error // suppressed exceptions print wrong in idea
    }

    actual fun runTest(block: suspend CoroutineScope.() -> Unit): TestResult =
        runTestWithRealTime(timeout = timeout, testBody = block)
}

private class UnhandledErrorsException(override val message: String) : Exception()
