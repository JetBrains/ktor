/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import ktorbuild.internal.capitalized
import ktorbuild.internal.kotlin
import ktorbuild.internal.libs
import ktorbuild.maybeNamed
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
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

internal fun Project.configureJs() {
    kotlin {
        js { binaries.library() }

        sourceSets {
            jsTest.dependencies {
                implementation(npm("puppeteer", libs.versions.puppeteer.get()))
            }
        }
    }

    configureJsTestTasks(target = "js")
}


internal fun Project.configureWasmJs() {
    kotlin {
        sourceSets {
            wasmJsMain.dependencies {
                implementation(libs.kotlinx.browser)
            }
            wasmJsTest.dependencies {
                implementation(npm("puppeteer", libs.versions.puppeteer.get()))
            }
        }
    }

    configureJsTestTasks(target = "wasmJs")
}


internal fun Project.configureJsTestTasks(target: String) {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunJsBrowserTest) return

    tasks.maybeNamed("clean${target.capitalized()}BrowserTest") { onlyIf { false } }
    tasks.maybeNamed("${target}BrowserTest") { onlyIf { false } }
}
