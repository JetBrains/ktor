/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("java-library")
}

description = "Internal module for checking JPMS compliance"

val generateModuleInfo = tasks.register("generateModuleInfo") {
    val modules = rootProject.subprojects
        .filter { it.hasJavaModule }
        .map { it.javaModuleName() }
    inputs.property("modules", modules)

    val moduleInfoFile = layout.projectDirectory.file("src/main/java/module-info.java")
    outputs.file(moduleInfoFile)

    doLast {
        moduleInfoFile.asFile
            .apply {
                parentFile.mkdirs()
                createNewFile()
            }
            .writer().buffered().use { writer ->
                writer.write("module io.ktor.test.module {\n")
                modules.forEach { writer.write("\trequires $it;\n") }
                writer.write("}")
            }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateModuleInfo)

    val emptyClasspath = objects.fileCollection()
    doFirst {
        options.compilerArgs.addAll(listOf("--module-path", classpath.asPath))
        classpath = emptyClasspath
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    rootProject.subprojects
        .filter { it.hasJavaModule }
        .map {
            generateSequence(it) { it.parent }
                .toList()
                .dropLast(1)
                .reversed()
                .joinToString(":", prefix = ":") { it.name }
        }
        .forEach { api(project(it)) }
}

internal val Project.hasJavaModule: Boolean
    get() = plugins.hasPlugin("maven-publish") && name != "ktor-bom" && name != "ktor-java-modules-test" && name != "ktor-serialization-kotlinx-xml" && hasJvm
