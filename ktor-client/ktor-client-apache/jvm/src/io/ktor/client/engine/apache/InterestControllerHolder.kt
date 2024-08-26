/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import kotlinx.atomicfu.*
import org.apache.http.nio.*

/**
 * Holder class to guard reference to [IOControl] so one couldn't access it improperly.
 */
internal class InterestControllerHolder {
    /**
     * Contains [IOControl] only when it is suspended. One should steal it first before requesting input again.
     */
    private val interestController = atomic<IOControl?>(null)

    private val waitingInput = atomic(false)
    private val waitingOutput = atomic(false)

    /**
     * Flag showing if input is suspended
     */
    val inputSuspended: Boolean
        get() = waitingInput.value

    /**
     * Flag showing if output is suspended
     */
    val outputSuspended: Boolean
        get() = waitingOutput.value

    /**
     * Suspend input using [ioControl] and remember it so we may resume later.
     * @throws IllegalStateException if there is another control saved before that wasn't resumed
     */
    fun suspendInput(ioControl: IOControl) {
        waitingInput.value = true
        ioControl.suspendInput()
        interestController.update { before ->
            check(before == null || before === ioControl) { "IOControl is already published" }
            ioControl
        }
    }

    /**
     * Try to resume an io control previously saved. Does nothing if wasn't suspended or already resumed.
     * Stealing is atomic, so for every suspend invocation, only single resume is possible.
     */
    fun resumeInputIfPossible() {
        interestController.getAndSet(null)?.requestInput()
        waitingInput.value = false
    }

    /**
     * Suspend output using [ioControl] and remember it so we may resume later.
     * @throws IllegalStateException if there is another control saved before that wasn't resumed
     */
    fun suspendOutput(ioControl: IOControl) {
        waitingOutput.value = true
        ioControl.suspendOutput()
        interestController.update { before ->
            check(before == null || before === ioControl) { "IOControl is already published" }
            ioControl
        }
    }

    /**
     * Try to resume an io control previously saved. Does nothing if wasn't suspended or already resumed.
     * Stealing is atomic, so for every suspend invocation, only single resume is possible.
     */
    fun resumeOutputIfPossible() {
        interestController.getAndSet(null)?.requestOutput()
        waitingOutput.value = false
    }
}
