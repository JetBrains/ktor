/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ktlint-disable filename
package io.ktor.server.test.base

import io.ktor.junit.*
import io.ktor.junit.coroutines.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@CoroutinesTimeout(5 * 60 * 1000)
@ErrorCollectorTest
actual abstract class BaseTest actual constructor() {
    actual open val timeout: Duration = 60.seconds // not used

    private val errorCollector = ErrorCollector()

    var testMethod: Optional<Method> = Optional.empty()
    var testName: String = ""

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        val method = testInfo.testMethod
        testMethod = method
        testName = method.map { it.name }.orElse(testInfo.displayName)
    }

    actual open fun afterTest() {
        errorCollector.throwErrorIfPresent()
    }

    actual open fun beforeTest() {
    }

    actual fun collectUnhandledException(error: Throwable) {
        errorCollector += error
    }

    actual fun runTest(
        timeout: Duration,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult = runTestWithRealTime(CoroutineName("test-$testName"), timeout) {
        beforeTest()
        try {
            block()
        } finally {
            afterTest()
        }
    }
}
