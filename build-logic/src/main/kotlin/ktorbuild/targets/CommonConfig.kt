/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import ktorbuild.internal.kotlin
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke

internal fun Project.configureCommon() {
    kotlin {
        sourceSets {
            commonMain.dependencies {
                api(libs.kotlinx.coroutines.core)
            }

            commonTest.dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
