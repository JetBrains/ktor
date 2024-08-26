/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.kotlin.dsl.*

fun Project.filterSnapshotTests() {
    val build_snapshot_train: String? by extra
    if (!build_snapshot_train.toBoolean()) return

    println("Hacking test tasks, removing stress and flaky tests")
    subprojects {
        tasks.withType<Test>().all {
            exclude("**/*ServerSocketTest*")
            exclude("**/*NettyStressTest*")
            exclude("**/*CIOMultithreadedTest*")
            exclude("**/*testBlockingConcurrency*")
            exclude("**/*testBigFile*")
            exclude("**/*numberTest*")
            exclude("**/*testWithPause*")
            exclude("**/*WebSocketTest*")
            exclude("**/*PostTest*")
            exclude("**/*testCustomUrls*")
            exclude("**/*testStaticServeFromDir*")
            exclude("**/*testRedirect*")
            exclude("**/*CIOHttpsTest*")
        }
    }

    println("Manifest of kotlin-compiler-embeddable.jar")

    subprojects.filter { it.name == "ktor-client" }.forEach {
        configurations.matching { it.name == "kotlinCompilerClasspath" }.all {
            resolvedConfiguration.files.filter { it.name.contains("kotlin-compiler-embeddable") }.forEach {
                val manifest = zipTree(it).matching {
                    include("META-INF/MANIFEST.MF")
                }.files.first()

                manifest.readLines().forEach {
                    println(it)
                }
            }
        }
    }
}

fun Project.setupTrainForSubproject() {
    val build_snapshot_train: String? by extra
    if (!build_snapshot_train.toBoolean()) {
        return
    }

    val atomicfu_version: String by extra
    val coroutines_version: String by extra
    val serialization_version: String by extra

    extra["kotlin_version"] = rootProject.properties["kotlin_snapshot_version"]
    val kotlin_version: String by extra
    println("Using Kotlin $kotlin_version for project $this")
    val deployVersion = properties["DeployVersion"]
    if (deployVersion != null) version = deployVersion

    val skipSnapshotChecks = rootProject.properties["skip_snapshot_checks"] != null
    if (!skipSnapshotChecks) {
        check(version, atomicfu_version, "atomicfu")
        check(version, coroutines_version, "coroutines")
        check(version, serialization_version, "serialization")
    }
    repositories {
        mavenLocal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
}

private fun check(version: Any, libVersion: String, libName: String) {
    if (version != libVersion) {
        error("Current deploy version is $version, but $libName version is not overridden ($libVersion)")
    }
}
