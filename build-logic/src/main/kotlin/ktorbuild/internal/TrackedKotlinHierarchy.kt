/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

package ktorbuild.internal

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.konan.target.REMOVED_TARGET_MESSAGE

private typealias GroupedSourceSets = MutableMap<String, MutableSet<String>>

/** Constructs a new [KotlinHierarchyTemplate] tracking added targets and groups using the provided [tracker]. */
@Suppress("FunctionName")
fun TrackedKotlinHierarchyTemplate(
    tracker: KotlinHierarchyTracker,
    describe: KotlinHierarchyBuilder.Root.() -> Unit,
): KotlinHierarchyTemplate {
    val trackedRoot = KotlinHierarchyTrackedRoot(tracker as KotlinHierarchyTrackerImpl)
    trackedRoot.apply(describe)
    return KotlinHierarchyTemplate(describe)
}

interface KotlinHierarchyTracker {
    val targetSourceSets: Map<String, Set<String>>
    val groups: Map<String, Set<String>>
}

fun KotlinHierarchyTracker(): KotlinHierarchyTracker = KotlinHierarchyTrackerImpl.getOrCreate(
    name = null,
    targetSourceSets = mutableMapOf(),
    groups = mutableMapOf(),
)

@Suppress("DeprecatedCallableAddReplaceWith")
private class KotlinHierarchyTrackerImpl(
    private val groupName: String?,
    override val targetSourceSets: GroupedSourceSets,
    override val groups: GroupedSourceSets,
) : KotlinHierarchyBuilder, KotlinHierarchyTracker {

    private var targetsFrozen = false

    override fun withCompilations(predicate: (KotlinCompilation<*>) -> Boolean) {}
    override fun excludeCompilations(predicate: (KotlinCompilation<*>) -> Boolean) {}

    override fun group(name: String, build: KotlinHierarchyBuilder.() -> Unit) {
        val groupTracker = getOrCreate(name, targetSourceSets, groups).also(build)
        groupTracker.targetsFrozen = true
        groups.getValue(name).forEach(::addTarget)
    }

    //region Groups
    override fun withNative() {
        withApple()
        withLinux()
        withMingw()
        withAndroidNative()
    }

    override fun withApple() {
        withIos()
        withWatchos()
        withMacos()
        withTvos()
    }

    override fun withIos() {
        withIosArm64()
        withIosX64()
        withIosSimulatorArm64()
    }

    override fun withWatchos() {
        withWatchosArm32()
        withWatchosArm64()
        withWatchosX64()
        withWatchosDeviceArm64()
        withWatchosSimulatorArm64()
    }

    override fun withMacos() {
        withMacosArm64()
        withMacosX64()
    }

    override fun withTvos() {
        withTvosArm64()
        withTvosX64()
        withTvosSimulatorArm64()
    }

    override fun withMingw() {
        withMingwX64()
    }

    override fun withLinux() {
        withLinuxArm64()
        withLinuxX64()
    }

    override fun withAndroidNative() {
        withAndroidNativeX64()
        withAndroidNativeX86()
        withAndroidNativeArm32()
        withAndroidNativeArm64()
    }
    //endregion

    //region Actual targets
    override fun withJs() = addTarget("js")
    override fun withJvm() = addTarget("jvm")

    @Deprecated("Renamed to 'withWasmJs'", replaceWith = ReplaceWith("withWasmJs()"))
    override fun withWasm() = withWasmJs()
    override fun withWasmJs() = addTarget("wasmJs")
    override fun withWasmWasi() = addTarget("wasmWasi")

    @Deprecated("Renamed to 'withAndroidTarget'", replaceWith = ReplaceWith("withAndroidTarget()"))
    override fun withAndroid() = withAndroidTarget()
    override fun withAndroidTarget() = addTarget("android")
    override fun withAndroidNativeX64() = addTarget("androidNativeX64")
    override fun withAndroidNativeX86() = addTarget("androidNativeX86")
    override fun withAndroidNativeArm32() = addTarget("androidNativeArm32")
    override fun withAndroidNativeArm64() = addTarget("androidNativeArm64")

    override fun withIosArm64() = addTarget("iosArm64")
    override fun withIosX64() = addTarget("iosX64")
    override fun withIosSimulatorArm64() = addTarget("iosSimulatorArm64")
    override fun withWatchosArm32() = addTarget("watchosArm32")
    override fun withWatchosArm64() = addTarget("watchosArm64")
    override fun withWatchosX64() = addTarget("watchosX64")
    override fun withWatchosSimulatorArm64() = addTarget("watchosSimulatorArm64")
    override fun withWatchosDeviceArm64() = addTarget("watchosDeviceArm64")
    override fun withTvosArm64() = addTarget("tvosArm64")
    override fun withTvosX64() = addTarget("tvosX64")
    override fun withTvosSimulatorArm64() = addTarget("tvosSimulatorArm64")

    override fun withLinuxArm64() = addTarget("linuxArm64")
    override fun withLinuxX64() = addTarget("linuxX64")
    override fun withMacosArm64() = addTarget("macosArm64")
    override fun withMacosX64() = addTarget("macosX64")
    override fun withMingwX64() = addTarget("mingwX64")
    //endregion

    //region Removed targets
    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withIosArm32() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withWatchosX86() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withMingwX86() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withLinuxArm32Hfp() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withLinuxMips32() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withLinuxMipsel32() = error("Target removed")

    @Deprecated(REMOVED_TARGET_MESSAGE, level = DeprecationLevel.ERROR)
    override fun withWasm32() = error("Target removed")
    //endregion

    private fun addTarget(name: String) {
        if (groupName == null) return
        check(!targetsFrozen) { "Can't add targets to already declared group: $groupName" }

        targetSourceSets.getOrPut(name) { mutableSetOf(name) }.add(groupName)
        groups.getOrPut(groupName) { mutableSetOf() }.add(name)
    }

    companion object {
        private val builders = hashMapOf<String?, KotlinHierarchyTrackerImpl>()

        fun getOrCreate(
            name: String?,
            targetSourceSets: GroupedSourceSets,
            groups: GroupedSourceSets,
        ): KotlinHierarchyTrackerImpl = builders.getOrPut(name) {
            KotlinHierarchyTrackerImpl(name, targetSourceSets, groups)
        }
    }
}

private class KotlinHierarchyTrackedRoot(
    tracker: KotlinHierarchyTrackerImpl,
) : KotlinHierarchyBuilder.Root, KotlinHierarchyBuilder by tracker {
    override fun excludeSourceSetTree(vararg tree: KotlinSourceSetTree) {}
    override fun sourceSetTrees(vararg tree: KotlinSourceSetTree) {}
    override fun withSourceSetTree(vararg tree: KotlinSourceSetTree) {}
}
