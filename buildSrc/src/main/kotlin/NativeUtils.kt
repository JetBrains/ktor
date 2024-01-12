/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*

val Project.COMMON_JVM_ONLY get() = IDEA_ACTIVE && properties.get("ktor.ide.jvmAndCommonOnly") == "true"

fun Project.fastOr(block: () -> List<String>): List<String> {
    if (COMMON_JVM_ONLY) return emptyList()
    return block()
}

fun Project.posixTargets(): List<String> = fastOr {
    nixTargets() + windowsTargets()
}

fun Project.nixTargets(): List<String> = fastOr {
    darwinTargets() + linuxTargets()
}

fun Project.linuxTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            linuxX64(),
            linuxArm64(),
        )
    }.map { it.name }
}

fun Project.darwinTargets(): List<String> = fastOr {
    macosTargets() + iosTargets() + watchosTargets() + tvosTargets()
}

fun Project.macosTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            macosX64(),
            macosArm64()
        ).map { it.name }
    }
}

fun Project.iosTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).map { it.name }
    }
}

fun Project.watchosTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            watchosX64(),
            watchosArm32(),
            watchosArm64(),
            watchosSimulatorArm64(),
        ).map { it.name }
    }
}

fun Project.tvosTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            tvosX64(),
            tvosArm64(),
            tvosSimulatorArm64(),
        ).map { it.name }
    }
}

fun Project.desktopTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            macosX64(),
            macosArm64(),
            linuxX64(),
            linuxArm64(),
            mingwX64()
        ).map { it.name }
    }
}

fun Project.windowsTargets(): List<String> = fastOr {
    with(kotlin) {
        listOf(
            mingwX64()
        ).map { it.name }
    }
}
