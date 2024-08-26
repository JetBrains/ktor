/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.util.*
import kotlin.coroutines.*

/**
 * This is a fake coroutine stack frame. It is reported by [SuspendFunctionGun] when the debug agent
 * is trying to probe jobs state by peeking frames when the coroutine is running at the same time
 * and the frames sequence is concurrently changed.
 */
internal object StackWalkingFailedFrame : CoroutineStackFrame, Continuation<Nothing> {
    override val callerFrame: CoroutineStackFrame? get() = null

    override fun getStackTraceElement(): StackTraceElement {
        return createStackTraceElement(
            StackWalkingFailed::class,
            StackWalkingFailed::failedToCaptureStackFrame.name,
            "StackWalkingFailed.kt",
            8
        )
    }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Nothing>) {
        StackWalkingFailed.failedToCaptureStackFrame()
    }
}
