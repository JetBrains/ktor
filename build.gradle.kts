/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.konan.target.HostManager

extra["globalM2"] = "${project.file("build")}/m2"
extra["publishLocal"] = project.hasProperty("publishLocal")

apply(from = "gradle/verifier.gradle")

extra["skipPublish"] = mutableListOf(
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-test-base",
)

// Point old artifact to new location
extra["relocatedArtifacts"] = mapOf(
    "ktor-server-test-base" to "ktor-server-test-host",
)

extra["nonDefaultProjectStructure"] = mutableListOf(
    "ktor-bom",
    "ktor-java-modules-test",
)

val disabledExplicitApiModeProjects = listOf(
    "ktor-client-tests",
    "ktor-server-test-base",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-client-content-negotiation-tests",
    "ktor-test-base"
)

apply(from = "gradle/compatibility.gradle")

plugins {
    id("ktorbuild.base")
    alias(libs.plugins.binaryCompatibilityValidator)
    conventions.gradleDoctor
}

println("Build version: ${project.version}")

subprojects {
    apply(plugin = "ktorbuild.base")

    group = "io.ktor"
    extra["hostManager"] = HostManager()

    setupTrainForSubproject()

    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (nonDefaultProjectStructure.contains(project.name)) return@subprojects

    apply(plugin = "ktorbuild.kmp")
    apply(plugin = "atomicfu-conventions")

    configureTargets()
    if (CI) configureTestTasksOnCi()

    configurations {
        maybeCreate("testOutput")
    }

    kotlin {
        if (!disabledExplicitApiModeProjects.contains(project.name)) explicitApi()

        configureSourceSets()

        compilerOptions {
            languageVersion = getKotlinLanguageVersion()
            apiVersion = getKotlinApiVersion()
        }
    }

    val skipPublish: List<String> by rootProject.extra
    if (!skipPublish.contains(project.name)) {
        configurePublication()
    }

    configureCodestyle()
}

println("Using Kotlin compiler version: ${libs.versions.kotlin.get()}")
filterSnapshotTests()

fun configureDokka() {
    allprojects {
        plugins.apply("ktorbuild.dokka")
    }

    rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().ignoreScripts = false
    }
}

configureDokka()

subprojects {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        configureCompilerOptions()
    }
}

fun KotlinMultiplatformExtension.configureSourceSets() {
    sourceSets
        .matching { it.name !in listOf("main", "test") }
        .all {
            val srcDir = if (name.endsWith("Main")) "src" else "test"
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
            val platform = name.dropLast(4)

            kotlin.srcDir("$platform/$srcDir")
            resources.srcDir("$platform/${resourcesPrefix}resources")

            languageSettings.apply {
                progressiveMode = true
            }
        }
}
