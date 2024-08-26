/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.util.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal class SuspendFunctionGun<TSubject : Any, TContext : Any>(
    initial: TSubject,
    context: TContext,
    private val blocks: List<PipelineInterceptor<TSubject, TContext>>
) : PipelineContext<TSubject, TContext>(context) {

    override val coroutineContext: CoroutineContext get() = continuation.context

    // this is impossible to inline because of property name clash
    // between PipelineContext.context and Continuation.context
    internal val continuation: Continuation<Unit> = object : Continuation<Unit>, CoroutineStackFrame {
        override val callerFrame: CoroutineStackFrame? get() = peekContinuation() as? CoroutineStackFrame

        var currentIndex: Int = Int.MIN_VALUE

        override fun getStackTraceElement(): StackTraceElement? = null

        private fun peekContinuation(): Continuation<*>? {
            if (currentIndex == Int.MIN_VALUE) currentIndex = lastSuspensionIndex
            if (currentIndex < 0) {
                currentIndex = Int.MIN_VALUE
                return null
            }
            // this is only invoked by debug agent during job state probes
            // lastPeekedIndex is non-volatile intentionally
            // and the list of continuations is not synchronized too
            // so this is not guaranteed to work properly (may produce incorrect trace),
            // but the only we care is to not crash here
            // and simply return StackWalkingFailedFrame on any unfortunate accident

            try {
                val result = suspensions[currentIndex] ?: return StackWalkingFailedFrame
                currentIndex -= 1
                return result
            } catch (_: Throwable) {
                return StackWalkingFailedFrame
            }
        }

        override val context: CoroutineContext
            get() {
                val continuation = suspensions[lastSuspensionIndex]
                if (continuation !== this && continuation != null) return continuation.context

                var index = lastSuspensionIndex - 1
                while (index >= 0) {
                    val cont = suspensions[index--]
                    if (cont !== this && cont != null) return cont.context
                }

                error("Not started")
            }

        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                resumeRootWith(Result.failure(result.exceptionOrNull()!!))
                return
            }

            loop(false)
        }
    }

    override var subject: TSubject = initial

    private val suspensions: Array<Continuation<TSubject>?> = arrayOfNulls(blocks.size)
    private var lastSuspensionIndex: Int = -1
    private var index = 0

    override fun finish() {
        index = blocks.size
    }

    override suspend fun proceed(): TSubject = suspendCoroutineUninterceptedOrReturn { continuation ->
        if (index == blocks.size) return@suspendCoroutineUninterceptedOrReturn subject

        addContinuation(continuation.intercepted())

        if (loop(true)) {
            discardLastRootContinuation()
            return@suspendCoroutineUninterceptedOrReturn subject
        }

        COROUTINE_SUSPENDED
    }

    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        if (index == blocks.size) return initial
        subject = initial

        if (lastSuspensionIndex >= 0) throw IllegalStateException("Already started")

        return proceed()
    }

    /**
     * @return `true` if it is possible to return result immediately
     */
    private fun loop(direct: Boolean): Boolean {
        do {
            val currentIndex = index // it is important to read index every time
            if (currentIndex == blocks.size) {
                if (!direct) {
                    resumeRootWith(Result.success(subject))
                    return false
                }

                return true
            }

            index = currentIndex + 1 // it is important to increase it before function invocation
            val next = blocks[currentIndex]

            try {
                val result = pipelineStartCoroutineUninterceptedOrReturn(next, this, subject, continuation)
                if (result === COROUTINE_SUSPENDED) return false
            } catch (cause: Throwable) {
                resumeRootWith(Result.failure(cause))
                return false
            }
        } while (true)
    }

    private fun resumeRootWith(result: Result<TSubject>) {
        if (lastSuspensionIndex < 0) error("No more continuations to resume")
        val next = suspensions[lastSuspensionIndex]!!
        suspensions[lastSuspensionIndex--] = null

        if (!result.isFailure) {
            next.resumeWith(result)
        } else {
            val exception = recoverStackTraceBridge(result.exceptionOrNull()!!, next)
            next.resumeWithException(exception)
        }
    }

    private fun discardLastRootContinuation() {
        if (lastSuspensionIndex < 0) throw IllegalStateException("No more continuations to resume")
        suspensions[lastSuspensionIndex--] = null
    }

    internal fun addContinuation(continuation: Continuation<TSubject>) {
        suspensions[++lastSuspensionIndex] = continuation
    }
}
