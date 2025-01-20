/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun Project.kotlin(block: KotlinMultiplatformExtension.() -> Unit) {
    configure(block)
}

val Project.kotlin: KotlinMultiplatformExtension get() = the()

fun NamedDomainObjectContainer<KotlinSourceSet>.commonMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = getByName("commonMain")
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.commonTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = getByName("commonTest")
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jvmAndPosixMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("jvmAndPosixMain") ?: getByName("jvmMain")
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jvmAndPosixTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("jvmAndPosixTest") ?: getByName("jvmTest")
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.nixTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("nixTest") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.posixMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("posixMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.darwinMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("darwinMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.darwinTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("darwinTest") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jsMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("jsMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.jsTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("jsTest") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.wasmJsMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("wasmJsMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.wasmJsTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("wasmJsTest") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.desktopMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("desktopMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.desktopTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("desktopTest") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.windowsMain(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("windowsMain") ?: return
    block(sourceSet)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.windowsTest(block: KotlinSourceSet.() -> Unit) {
    val sourceSet = findByName("windowsTest") ?: return
    block(sourceSet)
}

