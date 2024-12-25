/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.internal.gradle.finalizedOnRead
import ktorbuild.targets.KtorTargets
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

abstract class KtorBuildExtension(
    objects: ObjectFactory,
    val targets: KtorTargets
) {

    @Inject
    constructor(objects: ObjectFactory) : this(objects, objects.newInstance())

    /**
     * The JDK version to be used to build the project.
     * By default, the minimal supported JDK version is used.
     */
    val jvmToolchain: Property<JavaLanguageVersion> =
        objects.property<JavaLanguageVersion>()
            .convention(DEFAULT_JDK)
            .finalizedOnRead()

    fun jvmToolchain(version: Int) {
        jvmToolchain.set(JavaLanguageVersion.of(version))
    }

    companion object {
        const val NAME = "ktorBuild"

        /** The default (minimal) JDK version used for building the project. */
        private val DEFAULT_JDK = JavaLanguageVersion.of(8)
    }
}
