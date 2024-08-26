/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.ir.*
import java.io.*

fun Project.configureWasm() {
    configureWasmTasks()

    kotlin {
        sourceSets {
            val wasmJsMain by getting {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-browser:${Versions.browser}")
                }
            }
            val wasmJsTest by getting {
                dependencies {
                    implementation(npm("puppeteer", Versions.puppeteer))
                }
            }
        }
    }

    configureWasmTestTasks()
}

private fun Project.configureWasmTasks() {
    kotlin {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            nodejs {
                testTask {
                    useMocha {
                        timeout = "10000"
                    }
                }
            }

            (this as KotlinJsIrTarget).whenBrowserConfigured {
                testTask {
                    useKarma {
                        useChromeHeadless()
                        useConfigDirectory(File(project.rootProject.projectDir, "karma"))
                    }
                }
            }
        }
    }
}

private fun Project.configureWasmTestTasks() {
    val shouldRunWasmBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunWasmBrowserTest) return

    tasks.findByName("cleanWasmJsBrowserTest")?.onlyIf { false }
    tasks.findByName("wasmJsBrowserTest")?.onlyIf { false }
}
