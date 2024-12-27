/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

package ktorbuild.internal.gradle

import org.gradle.platform.OperatingSystem

internal fun OperatingSystem.isLinux() = this == OperatingSystem.LINUX
internal fun OperatingSystem.isWindows() = this == OperatingSystem.WINDOWS
