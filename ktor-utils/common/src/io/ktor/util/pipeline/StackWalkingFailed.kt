/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

internal object StackWalkingFailed {
    fun failedToCaptureStackFrame() {
        error(
            "Failed to capture stack frame. This is usually happens when a coroutine is running so" +
                " the frame stack is changing quickly " +
                "and the coroutine debug agent is unable to capture it concurrently." +
                " You may retry running your test to see this particular trace."
        )
    }
}
