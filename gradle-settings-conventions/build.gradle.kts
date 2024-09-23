/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradle:develocity-gradle-plugin:3.18.1")
    implementation("com.gradle:common-custom-user-data-gradle-plugin:2.0.1")
}
