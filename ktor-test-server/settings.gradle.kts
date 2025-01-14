/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    includeBuild("../build-settings-logic")
}

plugins {
    id("conventions-dependency-resolution-management")
}

rootProject.name = "ktor-test-server"
