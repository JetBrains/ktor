/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.ktorBuild
import org.gradle.api.services.internal.RegisteredBuildServiceProvider

plugins {
    id("ktorbuild.base")
    id("com.osacky.doctor")
}

doctor {
    enableTestCaching = false

    // Disable JAVA_HOME validation as we use "Daemon JVM discovery" feature
    // https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria
    javaHome {
        ensureJavaHomeIsSet = false
        ensureJavaHomeMatches = false
    }
}

// Always monitor tasks on CI, but disable it locally by default with providing an option to opt-in.
// See 'doctor.enableTaskMonitoring' in gradle.properties for details.
val enableTasksMonitoring = ktorBuild.isCI.get() ||
    findProperty("doctor.enableTaskMonitoring")?.toString().toBoolean()

if (!enableTasksMonitoring) {
    logger.info("Gradle Doctor task monitoring is disabled.")
    gradle.sharedServices.unregister("listener-service")
}

fun BuildServiceRegistry.unregister(name: String) {
    val registration = registrations.getByName(name)
    registrations.remove(registration)
    (registration.service as RegisteredBuildServiceProvider<*, *>).maybeStop()
}
