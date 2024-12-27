/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import ktorbuild.internal.gradle.*
import ktorbuild.internal.ktorBuild
import ktorbuild.maybeNamed
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("ktorbuild.base")
    kotlin("multiplatform")
}

kotlin {
    compilerOptions {
        progressiveMode = true
        apiVersion = ktorBuild.kotlinApiVersion
        languageVersion = ktorBuild.kotlinLanguageVersion
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

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

val targets = ktorBuild.targets

configureCommon()
if (targets.hasJvm) configureJvm()
if (targets.hasJs) configureJs()
if (targets.hasWasmJs) configureWasmJs()

if (targets.hasJsOrWasmJs) {
    tasks.configureEach {
        if (name == "compileJsAndWasmSharedMainKotlinMetadata") enabled = false
    }
}

// Run native tests only on matching host.
// There is no need to configure `onlyIf` for Darwin targets as they're configured by KGP.
@Suppress("UnstableApiUsage")
if (targets.hasNative) {
    tasks.maybeNamed("linkDebugTestLinuxX64") {
        onlyIf("run only on Linux") { ktorBuild.os.get().isLinux() }
    }
    tasks.maybeNamed("linkDebugTestLinuxArm64") {
        onlyIf("run only on Linux") { ktorBuild.os.get().isLinux() }
    }
    tasks.maybeNamed("linkDebugTestMingwX64") {
        onlyIf("run only on Windows") { ktorBuild.os.get().isWindows() }
    }
}

if (ktorBuild.isCI.get()) configureTestTasksOnCi()
