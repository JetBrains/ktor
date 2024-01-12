// ktlint-disable filename
/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.junit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import org.junit.jupiter.api.*
import java.lang.reflect.*
import java.util.*
import kotlin.time.*
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

    @AfterEach
    fun throwErrors() {
        errorCollector.throwErrorIfPresent()
    }

    actual fun collectUnhandledException(error: Throwable) {
        errorCollector += error
    }

    actual fun runTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(CoroutineName("test-$testName"), block)
    }
}
