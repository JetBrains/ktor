/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("test-server")
}

ktorBuild {
    // Package java.net.http was introduced in Java 11
    jvmToolchain(11)
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            implementation(libs.kotlinx.coroutines.jdk8)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
