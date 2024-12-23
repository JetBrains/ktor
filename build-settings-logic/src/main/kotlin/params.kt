/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

const val DEVELOCITY_SERVER = "https://ge.jetbrains.com"
const val GITHUB_REPO = "https://github.com/ktorio/ktor"
const val TEAMCITY_URL = "https://ktor.teamcity.com"

val isCIRun = System.getenv("TEAMCITY_VERSION") != null
