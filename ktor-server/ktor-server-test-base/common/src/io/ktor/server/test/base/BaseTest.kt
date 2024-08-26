/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.time.*

expect abstract class BaseTest() {
    open val timeout: Duration
    fun collectUnhandledException(error: Throwable) // TODO: better name?
    fun runTest(block: suspend CoroutineScope.() -> Unit): TestResult
}
