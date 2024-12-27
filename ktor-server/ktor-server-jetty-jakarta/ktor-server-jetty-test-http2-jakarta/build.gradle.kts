/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

ktorBuild {
    // The minimal JVM version required for Jetty 10+
    jvmToolchain(11)
}

kotlin.sourceSets {
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-base"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(libs.jetty.servlet.jakarta)
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-jetty-jakarta"))
        }
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")
}
