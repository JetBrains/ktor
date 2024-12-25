/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.initialization.Environment
import java.io.File
import javax.inject.Inject

/**
 * A utility class providing access to project properties declared in `gradle.properties`
 * files from the project directory up to the root project. The properties closer to the
 * current project directory take precedence over properties in parent directories.
 *
 * It is a workaround for the issue when `ProviderFactory.gradleProperty` and `GradleProperties`
 * take into account only root `gradle.properties`.
 * TODO: Remove when the issue is fixed
 *   https://github.com/gradle/gradle/issues/23572
 */
internal abstract class ProjectGradleProperties(
    private val layout: ProjectLayout,
    private val environment: Environment,
    private val rootDir: File,
) {

    @Suppress("unused")
    @Inject
    constructor(
        layout: ProjectLayout,
        environment: Environment,
        project: Project,
    ) : this(layout, environment, project.rootDir)

    private val properties: Map<String, String> by lazy {
        val properties = mutableMapOf<String, String>()
        layout.projectDirectory.asFile
            .walkUpToRoot(rootDir)
            .map { it.resolve(Project.GRADLE_PROPERTIES) }
            .mapNotNull(environment::propertiesFile)
            // Properties closer to the current project take precedence
            .forEach(properties::putAllAbsent)

        properties
    }

    fun byNamePrefix(prefix: String): Map<String, String> {
        return properties.filterKeys { it.startsWith(prefix) }
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
    }
}

//region Utils
private fun File.walkUpToRoot(rootDir: File): Sequence<File> = sequence {
    var current = absoluteFile
    while(true) {
        yield(current)
        if (current == rootDir || current.parent == null) break
        current = current.parentFile
    }
}

private fun <K, V> MutableMap<K, V>.putAllAbsent(other: Map<K, V>) {
    for ((key, value) in other) {
        if (key !in this) this[key] = value
    }
}
//endregion
