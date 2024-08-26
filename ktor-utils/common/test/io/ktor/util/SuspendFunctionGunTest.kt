/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.test.dispatcher.*
import io.ktor.util.pipeline.*
import kotlin.coroutines.*
import kotlin.test.*

class SuspendFunctionGunTest {
    @Test
    fun throwsErrorWhenNoDistinctContinuation() = testSuspend {
        val gun = SuspendFunctionGun(Unit, Unit, listOf({ _ -> }))
        gun.addContinuation(gun.continuation)

        val cause = assertFailsWith<IllegalStateException> { gun.continuation.context }
        assertEquals("Not started", cause.message)
    }

    @Test
    fun returnsLastDistinctContinuationContext() = testSuspend {
        val gun = SuspendFunctionGun(Unit, Unit, listOf({ _ -> }, { _ -> }))
        val continuation = Continuation<Unit>(EmptyCoroutineContext) {}
        gun.addContinuation(continuation)
        gun.addContinuation(gun.continuation)

        assertEquals(gun.continuation.context, continuation.context)
    }

    @Test
    fun returnsFirstDistinctContinuationContext() = testSuspend {
        val gun = SuspendFunctionGun(Unit, Unit, listOf({ _ -> }))
        val continuation = Continuation<Unit>(EmptyCoroutineContext) {}
        gun.addContinuation(continuation)

        assertEquals(gun.continuation.context, continuation.context)
    }
}
