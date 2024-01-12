/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.junit

import org.junit.jupiter.api.extension.*

@ExtendWith(ErrorCollector::class)
annotation class ErrorCollectorTest

class ErrorCollector : TestExecutionExceptionHandler, ParameterResolver, AfterEachCallback {

    private val errors = mutableListOf<Throwable>()

    operator fun plusAssign(th: Throwable) {
        errors += th
    }

    override fun handleTestExecutionException(context: ExtensionContext?, throwable: Throwable?) {
        throwable?.let {
            this@ErrorCollector += throwable
        }
    }

    override fun afterEach(context: ExtensionContext?) {
        throwErrorIfPresent()
    }

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean {
        return parameterContext != null && parameterContext.parameter.type == ErrorCollector::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any {
        return this
    }

    fun throwErrorIfPresent() {
        val currentErrors = errors.toList()
        errors.clear()
        when {
            currentErrors.isEmpty() -> {}
            currentErrors.size == 1 -> throw currentErrors.single()
            else -> {
                for (e in currentErrors)
                    e.printStackTrace()

                throw MultipleFailureException(currentErrors)
            }
        }
    }
}
