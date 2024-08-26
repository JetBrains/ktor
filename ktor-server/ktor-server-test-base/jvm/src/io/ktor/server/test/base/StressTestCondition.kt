/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import org.junit.jupiter.api.extension.*

class StressTestCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult =
        when (val property = System.getProperty("enable.stress.tests")) {
            in setOf(null, "false", "0") -> ConditionEvaluationResult.disabled("enable.stress.tests is $property")
            else -> ConditionEvaluationResult.enabled("enable.stress.tests is $property")
        }
}
