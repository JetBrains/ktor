/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import ktorbuild.KtorBuildExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/*
 * Gradle doesn't generate accessors for plugins defined in this module, so we have to declare them manually.
 */

internal val Project.ktorBuild: KtorBuildExtension get() = extensions.getByType()
