/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

            api(libs.logback.classic)
        }
    }
}

val jetty_alpn_boot_version: String? by extra
dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", libs.jetty.alpn.boot)
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")

    if (jetty_alpn_boot_version != null && JavaVersion.current() == JavaVersion.VERSION_1_8) {
        val bootClasspath = configurations.named("boot").get().files
        jvmArgs(bootClasspath.map { "-Xbootclasspath/p:${it.absolutePath}" }.iterator())
    }
}
