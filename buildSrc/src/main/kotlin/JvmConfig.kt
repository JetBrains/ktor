/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

fun Project.configureJvm() {
    val jdk = when (name) {
        in jdk11Modules -> 11
        else -> 8
    }

    val configuredVersion: String by rootProject.extra

    kotlin {
        jvm()

        sourceSets.apply {
            val jvmMain by getting {
                dependencies {
                    if (jdk > 6) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlin}")
                    }
                    if (jdk > 7) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}")
                        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${Versions.coroutines}") {
                            exclude(module = "kotlin-stdlib")
                            exclude(module = "kotlin-stdlib-jvm")
                            exclude(module = "kotlin-stdlib-jdk8")
                            exclude(module = "kotlin-stdlib-jdk7")
                        }
                    }

                    api("org.slf4j:slf4j-api:${Versions.slf4j}")
                }
            }

            val jvmTest by getting {
                dependencies {
                    implementation(kotlin("test-junit5"))
                    implementation("org.junit.jupiter:junit-jupiter:${Versions.junit}")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${Versions.coroutines}")
                }
            }
        }
    }

    tasks.register<Jar>("jarTest") {
        dependsOn(tasks.getByName("jvmTestClasses"))
        archiveClassifier.set("test")
        from(kotlin.targets.getByName("jvm").compilations.getByName("test").output)
    }

    configurations.apply {
        val testCompile = findByName("testCompile") ?: return@apply

        val testOutput by creating {
            extendsFrom(testCompile)
        }
        val boot by creating {
        }
    }

    val jvmTest: KotlinJvmTest = tasks.getByName<KotlinJvmTest>("jvmTest") {
        ignoreFailures = true
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
    }

    tasks.create<Test>("stressTest") {
        classpath = files(jvmTest.classpath)
        testClassesDirs = files(jvmTest.testClassesDirs)

        ignoreFailures = true
        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
    }

    tasks.getByName<Jar>("jvmJar").apply {
        manifest {
            attributes(
                "Implementation-Title" to name,
                "Implementation-Version" to configuredVersion
            )
            val name = project.javaModuleName()
            attributes("Automatic-Module-Name" to name)
        }
    }
}

/**
 * JUnit 5 requires Java 11+
 */
fun Test.configureJavaLauncher(jdk: Int) {
    if (jdk < 11) {
        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        val customLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of("11")
        }
        javaLauncher = customLauncher
    }
}

fun Project.javaModuleName(): String {
    return (if (this.name.startsWith("ktor-")) "io.${project.name}" else "io.ktor.${project.name}")
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
