/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.api.Project

/**
 * Resolves the version for the current project based on the defined properties.
 * Properties "releaseVersion" and "eapVersion" are passed on CI as build parameters:
 * ```
 * ./gradlew build -PreleaseVersion=3.0.0
 * ```
 */
internal fun Project.resolveVersion(): String {
    val projectVersion = version.toString().removeSuffix("-SNAPSHOT")
    val releaseVersion = findProperty("releaseVersion")?.toString()
    val eapVersion = findProperty("eapVersion")?.toString()

    return when {
        releaseVersion != null -> releaseVersion
        eapVersion != null -> "$projectVersion-eap-$eapVersion"
        else -> projectVersion
    }
}
