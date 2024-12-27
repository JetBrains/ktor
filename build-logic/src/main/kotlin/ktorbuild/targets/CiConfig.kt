/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import com.gradle.develocity.agent.gradle.test.TestRetryConfiguration
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

/** Applies CI-specific configurations to test tasks. */
internal fun Project.configureTestTasksOnCi() {
    // Don't fail build on the CI:
    // 1. To distinct builds failed because of failed tests and because of compilation errors or anything else.
    //    TeamCity parses test results to define build status, so the build won't be green.
    // 2. To run as many tests as possible while keeping fail-fast behavior locally.
    tasks.withType<AbstractTestTask>().configureEach {
        ignoreFailures = true
        if (this is KotlinTest) ignoreRunFailures = true
    }
    // KotlinTestReport overwrites ignoreFailure values and fails build on test failure if this flag is disabled
    extra["kotlin.tests.individualTaskReports"] = true

    tasks.withType<KotlinJvmTest>().configureEach {
        testRetry {
            maxRetries = 1
            maxFailures = 10
        }

        applyTestRetryCompatibilityWorkaround()
    }
}

/**
 * Test retry plugin is incompatible with test tasks that override the `createTestExecuter` method.
 * This includes the [KotlinJvmTest] task which wraps the test executor with its own wrapper.
 *
 * This workaround heavily relies on the internal implementation details of the test-retry plugin and KGP.
 *
 * The test retry plugin adds a `doFirst` action, which:
 * - Retrieves the test executer using `createTestExecuter` (KGP returns wrapped test executer here)
 * - Wraps it with `RetryTestExecuter`
 * - Sets the executer using `setTestExecuter`
 *
 * In the `doLast` action, it expects that `createTestExecuter` returns the previously created `RetryTestExecuter` instance.
 * However, KGP wraps every result of `createTestExecutor` with its own wrapper, resulting in the following nesting:
 *   KotlinJvmTarget$Executer(RetryTestExecuter(KotlinJvmTarget$Executer(DefaultTestExecuter)))
 *
 * KGP wraps the executer only if `targetName` is present, as it is needed to add the target name suffix to the test name.
 * The workaround sets `targetName` to `null` after the first KGP wrapper is created,
 * so `createTestExecuter` returns the previously created executer:
 *   RetryTestExecuter(KotlinJvmTarget$Executer(DefaultTestExecuter))
 *
 * Issue: https://github.com/gradle/test-retry-gradle-plugin/issues/116 (KT-49155)
 */
private fun KotlinJvmTest.applyTestRetryCompatibilityWorkaround() {
    if (targetName == null) return
    val originalTargetName = targetName

    val executeTestsActionIndex = taskActions.indexOfLast { it.displayName == "Execute executeTests" }
    check(executeTestsActionIndex != -1) { "Action executeTests not found" }

    // Add the workaround action and then move it to the correct position right before tests execution.
    doFirst("workaround for compatibility with testRetry") { targetName = null }
    val injectedAction = taskActions.removeFirst()
    taskActions.add(executeTestsActionIndex, injectedAction)

    // Restore targetName value as other plugins might rely on it.
    // For example, kover uses it to find test tasks by target name
    doLast("restore targetName") { targetName = originalTargetName }
}

// Docs: https://docs.gradle.com/develocity/gradle-plugin/current/#test_retry
private fun Test.testRetry(configure: TestRetryConfiguration.() -> Unit) {
    extensions.getByName<DevelocityTestConfiguration>("develocity").testRetry(configure)
}
