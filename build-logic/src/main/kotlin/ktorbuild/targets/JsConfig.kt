/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

internal fun KotlinJsTargetDsl.addSubTargets(targets: KtorTargets) {
    if (targets.isEnabled("${targetName}.nodeJs")) nodejs { useMochaForTests() }
    if (targets.isEnabled("${targetName}.browser")) browser { useKarmaForTests() }
}

private fun KotlinJsSubTargetDsl.useMochaForTests() {
    testTask {
        useMocha {
            // Disable timeout as we use individual timeouts for tests
            timeout = "0"
        }
    }
}

private fun KotlinJsSubTargetDsl.useKarmaForTests() {
    testTask {
        useKarma {
            useChromeHeadless()
            useConfigDirectory(project.rootProject.file("karma"))
        }
    }
}
