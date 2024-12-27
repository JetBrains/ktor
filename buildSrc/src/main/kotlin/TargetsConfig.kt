/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.io.File

private val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()
val Project.hasCommon: Boolean get() = files.any { it.name == "common" }
val Project.hasNonJvm: Boolean get() = files.any { it.name == "nonJvm" }
val Project.hasJvmAndPosix: Boolean get() = hasCommon || files.any { it.name == "jvmAndPosix" }
val Project.hasPosix: Boolean get() = hasCommon || hasNonJvm || hasJvmAndPosix || files.any { it.name == "posix" }
val Project.hasNix: Boolean get() = hasPosix || files.any { it.name == "nix" }
val Project.hasLinux: Boolean get() = hasNix || files.any { it.name == "linux" }
val Project.hasDarwin: Boolean get() = hasNix || files.any { it.name == "darwin" }
val Project.hasAndroidNative: Boolean get() = hasPosix || files.any { it.name == "androidNative" }
val Project.hasWindows: Boolean get() = hasPosix || files.any { it.name == "windows" }
val Project.hasJsAndWasmShared: Boolean get() = hasCommon || hasNonJvm || files.any { it.name == "jsAndWasmShared" }
val Project.hasJs: Boolean get() = hasJsAndWasmShared || files.any { it.name == "js" }
val Project.hasWasmJs: Boolean get() = hasJsAndWasmShared || files.any { it.name == "wasmJs" }
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndPosix || files.any { it.name == "jvm" }
