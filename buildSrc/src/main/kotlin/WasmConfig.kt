/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke

fun Project.configureWasm() {
    kotlin {
        sourceSets {
            wasmJsMain {
                dependencies {
                    implementation(libs.kotlinx.browser)
                }
            }
            wasmJsTest {
                dependencies {
                    implementation(npm("puppeteer", libs.versions.puppeteer.get()))
                }
            }
        }
    }

    configureJsTestTasks(target = "wasmJs")
}
