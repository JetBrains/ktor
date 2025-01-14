/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import ktorbuild.internal.kotlin
import ktorbuild.internal.ktorBuild
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

internal fun Project.configureJvm() {
    kotlin {
        sourceSets {
            jvmMain.dependencies {
                api(libs.slf4j.api)
            }

            jvmTest.dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.debug)
            }
        }
    }

    configureTests()
    configureJarManifest()
}

private fun Project.configureTests() {
    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(ktorBuild.jvmToolchain, ktorBuild.jvmTestToolchain)
    }

    tasks.register<Test>("stressTest") {
        classpath = files(jvmTest.map { it.classpath })
        testClassesDirs = files(jvmTest.map { it.testClassesDirs })

        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(ktorBuild.jvmToolchain, ktorBuild.jvmTestToolchain)
    }
}

private fun Project.configureJarManifest() {
    tasks.named<Jar>("jvmJar") {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Automatic-Module-Name" to project.javaModuleName(),
            )
        }
    }
}

/** Configure tests against different JDK versions. */
private fun Test.configureJavaToolchain(
    compileJdk: Provider<JavaLanguageVersion>,
    testJdk: Provider<JavaLanguageVersion>,
) {
    val testJdkVersion = testJdk.get().asInt()
    onlyIf("only if testJdk is not lower than compileJdk") { testJdkVersion >= compileJdk.get().asInt() }

    val javaToolchains = project.the<JavaToolchainService>()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = testJdk
    }

    if (testJdkVersion >= 16) {
        // Allow reflective access from tests
        jvmArgs(
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        )
    }

    if (testJdkVersion >= 21) {
        // coroutines-debug use dynamic agent loading under the hood.
        // Remove as soon as the issue is fixed: https://youtrack.jetbrains.com/issue/KT-62096/
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}

fun Project.javaModuleName(): String {
    return (if (this.name.startsWith("ktor-")) "io.${project.name}" else "io.ktor.${project.name}")
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
