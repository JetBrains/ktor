/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.coroutines.*
import kotlin.time.*

/**
 * Test runner for js suspend tests.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.test.dispatcher.testSuspend)
 */
@OptIn(DelicateCoroutinesApi::class)
public actual fun testSuspend(
    context: CoroutineContext,
    timeoutMillis: Long,
    block: suspend CoroutineScope.() -> Unit
): TestResult = runTest(context = context, timeout = timeoutMillis.toDuration(DurationUnit.MILLISECONDS)) {
    block()
}
