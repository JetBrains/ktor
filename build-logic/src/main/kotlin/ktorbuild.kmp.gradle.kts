/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.ktorBuild
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("ktorbuild.base")
    kotlin("multiplatform")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KtorTargets.hierarchyTemplate)
    addTargets(ktorBuild.targets)

    // Specify JVM toolchain later to prevent it from being evaluated before it was configured.
    // TODO: Remove `afterEvaluate` when the BCV issue triggering JVM toolchain evaluation is fixed
    //   https://github.com/Kotlin/binary-compatibility-validator/issues/286
    afterEvaluate {
        jvmToolchain {
            languageVersion = ktorBuild.jvmToolchain
        }
    }
}
