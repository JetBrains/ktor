/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common extensions for JUnit 5 testing framework"

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(libs.kotlin.test.junit5)
            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }
}
