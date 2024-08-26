/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.*
import java.io.*

val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()
val Project.hasCommon: Boolean get() = files.any { it.name == "common" }
val Project.hasJvmAndPosix: Boolean get() = hasCommon || files.any { it.name == "jvmAndPosix" }
val Project.hasJvmAndNix: Boolean get() = hasCommon || files.any { it.name == "jvmAndNix" }
val Project.hasPosix: Boolean get() = hasCommon || hasJvmAndPosix || files.any { it.name == "posix" }
val Project.hasDesktop: Boolean get() = hasPosix || files.any { it.name == "desktop" }
val Project.hasNix: Boolean get() = hasPosix || hasJvmAndNix || files.any { it.name == "nix" }
val Project.hasLinux: Boolean get() = hasNix || files.any { it.name == "linux" }
val Project.hasDarwin: Boolean get() = hasNix || files.any { it.name == "darwin" }
val Project.hasWindows: Boolean get() = hasPosix || files.any { it.name == "windows" }
val Project.hasJsAndWasmShared: Boolean get() = files.any { it.name == "jsAndWasmShared" }
val Project.hasJs: Boolean get() = hasCommon || files.any { it.name == "js" } || hasJsAndWasmShared
val Project.hasWasm: Boolean get() = hasCommon || files.any { it.name == "wasmJs" } || hasJsAndWasmShared
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndNix || hasJvmAndPosix || files.any { it.name == "jvm" }
val Project.hasNative: Boolean
    get() = hasCommon || hasNix || hasPosix || hasLinux || hasDarwin || hasDesktop || hasWindows

fun Project.configureTargets() {
    configureCommon()
    if (hasJvm) configureJvm()

    kotlin {
        if (hasJs) {
            js {
                nodejs()
                // we don't test `server` modules in a browser.
                // there are 2 explanations why:
                // * logical - we don't need server in browser
                // * technical - we don't have access to files, os, etc.
                // Also, because of the ` origin ` URL in a browser, special support in `test-host` need to be implemented
                if (!project.name.startsWith("ktor-server")) browser()
            }

            configureJs()
        }

        if (hasWasm) {
            @OptIn(ExperimentalWasmDsl::class)
            wasmJs {
                nodejs()
                // we don't test `server` modules in a browser.
                if (!project.name.startsWith("ktor-server")) browser()
            }

            configureWasm()
        }

        if (hasPosix || hasLinux || hasDarwin || hasWindows) extra.set("hasNative", true)

        sourceSets {
            if (hasJsAndWasmShared) {
                val commonMain by getting {}
                val jsAndWasmSharedMain by creating {
                    dependsOn(commonMain)
                }
                val commonTest by getting {}
                val jsAndWasmSharedTest by creating {
                    dependsOn(commonTest)
                }

                jsMain {
                    dependsOn(jsAndWasmSharedMain)
                }
                jsTest {
                    dependsOn(jsAndWasmSharedTest)
                }
                wasmJsMain {
                    dependsOn(jsAndWasmSharedMain)
                }
                wasmJsTest {
                    dependsOn(jsAndWasmSharedTest)
                }
            }

            if (hasPosix) {
                val posixMain by creating
                val posixTest by creating
            }

            if (hasNix) {
                val nixMain by creating
                val nixTest by creating
            }

            if (hasDarwin) {
                val darwinMain by creating {
                    val nixMain = findByName("nixMain")
                    nixMain?.let { dependsOn(it) }

                    val posixMain = findByName("posixMain")
                    posixMain?.let { dependsOn(posixMain) }

                    val jvmAndNixMain = findByName("jvmAndNixMain")
                    jvmAndNixMain?.let { dependsOn(jvmAndNixMain) }

                    val commonMain = findByName("commonMain")
                    commonMain?.let { dependsOn(commonMain) }
                }
                val darwinTest by creating {
                    dependencies {
                        implementation(kotlin("test"))
                    }

                    val nixTest = findByName("nixTest")
                    nixTest?.let { dependsOn(nixTest) }

                    val posixTest = findByName("posixTest")
                    posixTest?.let { dependsOn(posixTest) }

                    val jvmAndNixTest = findByName("jvmAndNixTest")
                    jvmAndNixTest?.let { dependsOn(jvmAndNixTest) }

                    val commonTest = findByName("commonTest")
                    commonTest?.let { dependsOn(commonTest) }
                }

                val macosMain by creating
                val macosTest by creating

                val watchosMain by creating
                val watchosTest by creating

                val tvosMain by creating
                val tvosTest by creating

                val iosMain by creating
                val iosTest by creating
            }

            if (hasDesktop) {
                val desktopMain by creating {
                    val commonMain = findByName("commonMain")
                    commonMain?.let { dependsOn(commonMain) }
                }
                val desktopTest by creating {
                    val commonTest = findByName("commonTest")
                    commonTest?.let { dependsOn(commonTest) }
                }
            }

            if (hasLinux) {
                val linuxMain by creating
                val linuxTest by creating
            }

            if (hasWindows) {
                val windowsMain by creating
                val windowsTest by creating
            }

            if (hasJvmAndPosix) {
                val jvmAndPosixMain by creating {
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val jvmAndPosixTest by creating {
                    findByName("commonTest")?.let { dependsOn(it) }
                }
            }

            if (hasJvmAndNix) {
                val jvmAndNixMain by creating {
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val jvmAndNixTest by creating {
                    findByName("commonTest")?.let { dependsOn(it) }
                }
            }

            if (hasJvm) {
                val jvmMain by getting {
                    findByName("jvmAndNixMain")?.let { dependsOn(it) }
                    findByName("jvmAndPosixMain")?.let { dependsOn(it) }
                }

                val jvmTest by getting {
                    findByName("jvmAndNixTest")?.let { dependsOn(it) }
                    findByName("jvmAndPosixTest")?.let { dependsOn(it) }
                }
            }

            if (hasPosix) {
                val posixMain by getting {
                    findByName("commonMain")?.let { dependsOn(it) }
                    findByName("jvmAndPosixMain")?.let { dependsOn(it) }
                }

                val posixTest by getting {
                    findByName("commonTest")?.let { dependsOn(it) }
                    findByName("jvmAndPosixTest")?.let { dependsOn(it) }

                    dependencies {
                        implementation(kotlin("test"))
                    }
                }

                posixTargets().forEach {
                    getByName("${it}Main").dependsOn(posixMain)
                    getByName("${it}Test").dependsOn(posixTest)
                }
            }

            if (hasNix) {
                val nixMain by getting {
                    findByName("posixMain")?.let { dependsOn(it) }
                    findByName("jvmAndNixMain")?.let { dependsOn(it) }
                }

                val nixTest by getting {
                    findByName("posixTest")?.let { dependsOn(it) }
                    findByName("jvmAndNixTest")?.let { dependsOn(it) }
                }

                nixTargets().forEach {
                    getByName("${it}Main").dependsOn(nixMain)
                    getByName("${it}Test").dependsOn(nixTest)
                }
            }

            if (hasDarwin) {
                val nixMain: KotlinSourceSet? = findByName("nixMain")
                val nixTest: KotlinSourceSet? = findByName("nixTest")

                val darwinMain by getting
                val darwinTest by getting
                val macosMain by getting
                val macosTest by getting
                val iosMain by getting
                val iosTest by getting
                val watchosMain by getting
                val watchosTest by getting
                val tvosMain by getting
                val tvosTest by getting

                nixMain?.let { darwinMain.dependsOn(it) }
                macosMain.dependsOn(darwinMain)
                tvosMain.dependsOn(darwinMain)
                iosMain.dependsOn(darwinMain)
                watchosMain.dependsOn(darwinMain)

                nixTest?.let { darwinTest.dependsOn(it) }
                macosTest.dependsOn(darwinTest)
                tvosTest.dependsOn(darwinTest)
                iosTest.dependsOn(darwinTest)
                watchosTest.dependsOn(darwinTest)

                macosTargets().forEach {
                    getByName("${it}Main").dependsOn(macosMain)
                    getByName("${it}Test").dependsOn(macosTest)
                }

                iosTargets().forEach {
                    getByName("${it}Main").dependsOn(iosMain)
                    getByName("${it}Test").dependsOn(iosTest)
                }

                watchosTargets().forEach {
                    getByName("${it}Main").dependsOn(watchosMain)
                    getByName("${it}Test").dependsOn(watchosTest)
                }

                tvosTargets().forEach {
                    getByName("${it}Main").dependsOn(tvosMain)
                    getByName("${it}Test").dependsOn(tvosTest)
                }

                darwinTargets().forEach {
                    getByName("${it}Main").dependsOn(darwinMain)
                    getByName("${it}Test").dependsOn(darwinTest)
                }
            }

            if (hasLinux) {
                val linuxMain by getting {
                    findByName("nixMain")?.let { dependsOn(it) }
                }

                val linuxTest by getting {
                    findByName("nixTest")?.let { dependsOn(it) }

                    dependencies {
                        implementation(kotlin("test"))
                    }
                }

                linuxTargets().forEach {
                    getByName("${it}Main").dependsOn(linuxMain)
                    getByName("${it}Test").dependsOn(linuxTest)
                }
            }

            if (hasDesktop) {
                val desktopMain by getting {
                    findByName("posixMain")?.let { dependsOn(it) }
                }

                val desktopTest by getting {
                    findByName("posixTest")?.let { dependsOn(it) }
                }

                desktopTargets().forEach {
                    getByName("${it}Main").dependsOn(desktopMain)
                    getByName("${it}Test").dependsOn(desktopTest)
                }
            }

            if (hasWindows) {
                val windowsMain by getting {
                    findByName("posixMain")?.let { dependsOn(it) }
                    findByName("desktopMain")?.let { dependsOn(it) }
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val windowsTest by getting {
                    findByName("posixTest")?.let { dependsOn(it) }
                    findByName("desktopTest")?.let { dependsOn(it) }
                    findByName("commonTest")?.let { dependsOn(it) }

                    dependencies {
                        implementation(kotlin("test"))
                    }
                }

                windowsTargets().forEach {
                    getByName("${it}Main").dependsOn(windowsMain)
                    getByName("${it}Test").dependsOn(windowsTest)
                }
            }

            if (hasNative) {
                tasks.findByName("linkDebugTestLinuxX64")?.onlyIf { HOST_NAME == "linux" }
                tasks.findByName("linkDebugTestLinuxArm64")?.onlyIf { HOST_NAME == "linux" }
                tasks.findByName("linkDebugTestMingwX64")?.onlyIf { HOST_NAME == "windows" }
            }
        }
    }

    if (hasJsAndWasmShared) {
        tasks.configureEach {
            if (name == "compileJsAndWasmSharedMainKotlinMetadata") {
                enabled = false
            }
        }
    }
}
