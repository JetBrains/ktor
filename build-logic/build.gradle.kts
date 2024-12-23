/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)

    // A hack to make version catalogs accessible from buildSrc sources
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)
}
