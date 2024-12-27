/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.targets.KtorTargets
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Creates a CInterop configuration for all Native targets using the given [sourceSet]
 * in a Kotlin Multiplatform project.
 *
 * The [name] defines the CInterop configuration name. Definition file is expected to be located
 * at `[sourceSet]/interop/[name].def` by default, but can be customized via [definitionFilePath].
 * Additional configuration can be provided through [configure] block.
 *
 * Simple usage:
 * ```
 * kotlin {
 *     createCInterop("ssl", "posix")
 * }
 * ```
 *
 * Advanced usage with a separate definition for each target and additional configuration:
 * ```
 * kotlin {
 *     createCInterop(
 *         name = "ssl",
 *         sourceSet = "posix",
 *         definitionFilePath = { target -> "$target/interop/ssl.def" },
 *         configure = { target ->
 *             includeDirs("$target/interop/include")
 *             compilerOpts("-DUSE_SSL")
 *         }
 *     )
 * }
 * ```
 */
@Suppress("UnstableApiUsage")
fun KotlinMultiplatformExtension.createCInterop(
    name: String,
    sourceSet: String,
    definitionFilePath: (String) -> String = { "$sourceSet/interop/$name.def" },
    configure: DefaultCInteropSettings.(String) -> Unit = {}
) {
    val cinteropTargets = KtorTargets.resolveTargets(sourceSet)
    val projectDirectory = project.isolated.projectDirectory

    targets.named { it in cinteropTargets }
        .all {
            check(this is KotlinNativeTarget) { "Can't create cinterop for non-native target $targetName" }

            val main by compilations
            main.cinterops.create(name) {
                definitionFile = projectDirectory.file(definitionFilePath(targetName))
                configure(targetName)
            }
        }
}
