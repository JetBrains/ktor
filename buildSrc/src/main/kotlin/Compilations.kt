/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

fun KotlinCompilation<KotlinCommonOptions>.configureCompilation() {
    kotlinOptions {
//        if (platformType == KotlinPlatformType.jvm && !IDEA_ACTIVE) {
//            allWarningsAsErrors = true
//        }

        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-Xexpect-actual-classes"
    }
}
