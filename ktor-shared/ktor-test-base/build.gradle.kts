/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common extensions for testing Ktor"

plugins {
    id("ktorbuild.project.internal")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(libs.kotlin.test)
            api(project(":ktor-test-dispatcher"))
        }
    }

    jvmMain {
        dependencies {
            api(libs.kotlin.test.junit5)
            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }
}
