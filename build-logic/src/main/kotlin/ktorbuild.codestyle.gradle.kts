/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.ktorBuild

plugins {
    id("org.jmailen.kotlinter")
}

kotlinter {
    // Don't fail lint tasks on CI as we don't want TeamCity to show a build failure in addition to an actual lint report
    ignoreLintFailures = ktorBuild.isCI.get()
    reporters = arrayOf("checkstyle", "plain")
}
