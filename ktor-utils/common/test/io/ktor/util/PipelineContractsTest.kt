/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.collections.*
import io.ktor.util.pipeline.*
import kotlinx.atomicfu.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation", "DEPRECATION")
class PipelineContractsTest {
    private val checkList = mutableListOf<String>()
    private var v = 0
    private val caught = atomic(false)

    private val phase1 = PipelinePhase("A")
    private val phase2 = PipelinePhase("B")
    private val interceptor1: PipelineInterceptor<Unit, Unit> = { v = 1; checkList.add("1") }
    private val interceptor2: PipelineInterceptor<Unit, Unit> = { v = 2; checkList.add("2") }

    @Test
    fun testMergeEmpty() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        second.merge(first)
        assertTrue { first.isEmpty }
        assertTrue { second.isEmpty }

        assertSame(first.interceptorsForTests(), second.interceptorsForTests())
    }

    @Test
    fun testMergeSingle() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1) {}

        second.merge(first)

        assertSame(first.interceptorsForTests(), second.interceptorsForTests())
        assertSame(first.interceptorsForTests(), first.phaseInterceptors(phase1))
    }

    @Test
    fun testModifyAfterMerge() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1, interceptor1)

        second.merge(first)

        first.intercept(phase1, interceptor2)

        assertNotSame(first.interceptorsForTests(), second.interceptorsForTests())
        second.execute()
        assertEquals(listOf("1", "completed"), checkList)
    }

    @Test
    fun testLastPhase() {
        val first = Pipeline<Unit, Unit>(phase1, phase2)
        first.intercept(phase1, interceptor1)
        first.intercept(phase2, interceptor2)

        val before = first.interceptorsForTests()

        first.intercept(phase2, interceptor2)
        // adding an interceptor to the last phase shouldn't reallocate unshared list

        val after = first.interceptorsForTests()

        assertSame(before, after)

        // intercepting earlier phase should

        first.intercept(phase1, interceptor1)

        assertNotSame(before, first.interceptorsForTests())

        val second = Pipeline<Unit, Unit>(phase1, phase2)
        second.merge(first)
    }

    @Test
    fun testLastPhaseThenMerge() {
        val first = Pipeline<Unit, Unit>(phase1, phase2)
        first.intercept(phase1, interceptor1)
        first.intercept(phase2, interceptor2)

        val before = first.interceptorsForTests()

        first.intercept(phase2, interceptor2)
        // adding an interceptor to the last phase shouldn't reallocate unshared list

        val after = first.interceptorsForTests()

        assertSame(before, after)

        val second = Pipeline<Unit, Unit>(phase1, phase2)
        second.merge(first)

        // it should be shared during merge
        assertSame(before, second.interceptorsForTests())

        // intercepting first should reallocate
        first.intercept(phase2, interceptor2)
        assertNotSame(first.interceptorsForTests(), second.interceptorsForTests())
    }

    @Test
    fun executeEmptyPipelineSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.execute()

        assertEquals(listOf("completed"), checkList)
    }

    @Test
    fun testExecutePipelineSingleSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.execute()

        assertEquals(listOf("1", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineSimpleSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.intercept(phase1, interceptor2)
        pipeline.execute()

        assertEquals(listOf("1", "2", "completed"), checkList)
    }

    @Test
    fun executePipelineSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspension() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        var continuation: Continuation<Unit>? = null

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
            suspendCoroutine<Unit> {
                continuation = it
            }
            checkList.add("5")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4"), checkList)

        continuation!!.resume(Unit)
        assertEquals(listOf("1", "2", "4", "5", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspensionAndImmediateResume() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
            suspendCoroutineUninterceptedOrReturn<Unit> {
                it.resume(Unit)
                COROUTINE_SUSPENDED
            }
            checkList.add("5")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4", "5", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspensionAndNestedProceed() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        var continuation: Continuation<Unit>? = null

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            checkList.add("2")
            suspendCoroutineUninterceptedOrReturn<Unit> {
                continuation = it
                COROUTINE_SUSPENDED
            }
            checkList.add("3")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("4")
                proceed()
            } finally {
                checkList.add("5")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("6")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2"), checkList)

        continuation!!.resume(Unit)

        assertEquals(listOf("1", "2", "3", "4", "6", "5", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineTwiceTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.execute()
        pipeline.execute()

        assertEquals(listOf("1", "completed", "1", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineFailureTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1) {
            throw IllegalStateException()
        }

        pipeline.execute()
    }

    @Test
    fun testExecutePipelineCaughtFailureTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        class MyException : Exception()

        pipeline.intercept(phase1) {
            try {
                proceed()
            } catch (expected: MyException) {
                caught.value = true
            }
        }
        pipeline.intercept(phase1) {
            throw MyException()
        }

        pipeline.execute()
        assertTrue { caught.value }
    }

    private fun Pipeline<Unit, Unit>.execute() {
        val body = suspend {
            execute(Unit, Unit)
        }

        val completion: Continuation<Unit> = Continuation(EmptyCoroutineContext) {
            checkList.add("completed")
        }

        body.startCoroutine(completion)
    }
}
