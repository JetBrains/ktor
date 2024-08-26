/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.junit

import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.*
import java.util.stream.*
import kotlin.streams.*

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@TestTemplate
@Retention
annotation class RetryableTest(val retries: Int = 1, val delay: Long = 1_000L)

class RetrySupport : TestTemplateInvocationContextProvider {

    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return context.testMethod.isPresent && context.testMethod.get().isAnnotationPresent(RetryableTest::class.java)
    }

    override fun provideTestTemplateInvocationContexts(
        context: ExtensionContext
    ): Stream<TestTemplateInvocationContext> {
        val testMethod = context.testMethod.get()
        val annotation = testMethod.getAnnotation(RetryableTest::class.java)

        return RetryableTestContext(annotation.retries, annotation.delay)
            .getInvocationContexts()
            .asStream()
    }

    class RetryableTestContext(private val retries: Int, private val delay: Long) {

        private var lastException: Throwable? = null

        fun getInvocationContexts(): Sequence<TestTemplateInvocationContext> =
            when (retries) {
                0 -> sequenceOf(object : TestTemplateInvocationContext {})
                1 -> sequenceOf(FirstAttemptContext(), LastAttemptContext())
                else -> sequenceOf(FirstAttemptContext()) + generateSequence(::IntermediateRetryContext).take(
                    retries - 1
                ) + sequenceOf(LastAttemptContext())
            }

        inner class FirstAttemptContext : TestTemplateInvocationContext {
            override fun getDisplayName(invocationIndex: Int) = "First attempt"
            override fun getAdditionalExtensions() = mutableListOf(SetLastException())
        }

        open inner class IntermediateRetryContext : TestTemplateInvocationContext {
            override fun getDisplayName(invocationIndex: Int): String = "Retry #${invocationIndex - 1}"
            override fun getAdditionalExtensions() = mutableListOf(
                OnlyIfPreviousFailed(),
                SetLastException()
            )
        }

        inner class LastAttemptContext : IntermediateRetryContext() {
            override fun getAdditionalExtensions(): MutableList<Extension> =
                mutableListOf(OnlyIfPreviousFailed())
        }

        inner class OnlyIfPreviousFailed : ExecutionCondition {
            override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult =
                when (lastException) {
                    null -> ConditionEvaluationResult.disabled("Previous attempt passed")
                    else -> ConditionEvaluationResult.enabled("Previous attempt failed")
                }
        }

        inner class SetLastException : TestExecutionExceptionHandler {
            override fun handleTestExecutionException(context: ExtensionContext?, throwable: Throwable?) {
                throwable?.printStackTrace()
                Thread.sleep(delay)
                lastException = throwable
            }
        }
    }
}
