/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class SelectNixTest {
    @Test
    fun selectDescriptorIsEqualOrLargerThanFdSetSize() = testSuspend {
        val scope = CoroutineScope(
            CoroutineExceptionHandler { _, cause ->
                val regexStr = """File descriptor \d+ is larger or equal to FD_SETSIZE \(3\)"""
                val message = cause.message
                assertNotNull(message)
                assertTrue(
                    regexStr.toRegex().matches(message),
                    "Expected message in format \"$regexStr\", got $message"
                )
            }
        )

        val selector = SelectorHelper(3)
        val job = selector.start(scope)

        selector.interest(EventInfo(1, SelectInterest.READ, Continuation(coroutineContext) {}))
        selector.interest(EventInfo(2, SelectInterest.READ, Continuation(coroutineContext) {}))
        selector.interest(EventInfo(3, SelectInterest.READ, Continuation(coroutineContext) {}))

        launch {
            delay(1000)
            if (scope.isActive) {
                selector.requestTermination()
                job.cancel()
                fail("Exception should have been thrown")
            }
        }

        job.join()
    }

    @Test
    fun selectDescriptorIsNegative() = testSuspend {
        val scope = CoroutineScope(
            CoroutineExceptionHandler { _, cause ->
                assertEquals("File descriptor -1 is negative", cause.message)
            }
        )

        val selector = SelectorHelper()
        val job = selector.start(scope)

        selector.interest(EventInfo(-1, SelectInterest.READ, Continuation(coroutineContext) {}))

        launch {
            delay(1000)
            if (scope.isActive) {
                selector.requestTermination()
                job.cancel()
                fail("Exception should have been thrown")
            }
        }

        job.join()
    }
}
