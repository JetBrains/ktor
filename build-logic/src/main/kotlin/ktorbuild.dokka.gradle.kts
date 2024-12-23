/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.libs
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask

plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    dokkaPlugin(libs.dokka.plugin.versioning)
}

if (project == rootProject) {
    tasks.withType<DokkaMultiModuleTask>().configureEach {
        val version = project.version
        val dokkaOutputDir = "../versions"
        val id = "org.jetbrains.dokka.versioning.VersioningPlugin"
        val config = """{ "version": "$version", "olderVersionsDir":"$dokkaOutputDir" }"""

        outputDirectory = project.layout.projectDirectory.dir("$dokkaOutputDir/$version")
        pluginsMapConfiguration = mapOf(id to config)
    }
}
