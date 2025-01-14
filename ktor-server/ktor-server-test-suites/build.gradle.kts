/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-hsts"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))
            api(project(":ktor-server:ktor-server-test-base"))
        }
    }

    jvmMain {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-default-headers"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-request-validation"))
            implementation(libs.kotlinx.coroutines.debug)
        }
    }
}
