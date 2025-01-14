/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

/**
 * Accessor to make version catalog available in build-logic.
 * See: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
 */
internal val Project.libs: LibrariesForLibs
    get() = rootProject.the<LibrariesForLibs>()
