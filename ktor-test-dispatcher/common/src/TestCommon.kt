/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Test runner for common suspend tests.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.test.dispatcher.testSuspend)
 */
@Deprecated(
    "testSuspend is deprecated, use runTest function instead",
    replaceWith = ReplaceWith(
        "runTest { block() }",
        "kotlinx.coroutines.test.runTest",
    ),
    level = DeprecationLevel.WARNING
)
public expect fun testSuspend(
    context: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = 60L * 1000L,
    block: suspend CoroutineScope.() -> Unit
): TestResult

// kotlinx.coroutines.test.runTest uses `virtual` time by default, which is not what we want sometimes
// probably in almost all places it should be fine to use virtual time
// and change dispatcher in those places where needed
@OptIn(ExperimentalCoroutinesApi::class)
public fun runTestWithRealTime(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,
    testBody: suspend CoroutineScope.() -> Unit
): TestResult = runTest(context, timeout) {
    withContext(Dispatchers.Default.limitedParallelism(1), testBody)
}
