/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-servlet"))
                api(libs.jetty.server)
                api(libs.jetty.servlets)
                api(libs.jetty.alpn.server)
                api(libs.jetty.alpn.java.server)
                api(libs.jetty.alpn.openjdk8.server)
                api(libs.jetty.http2.server)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-base"))
                api(project(":ktor-server:ktor-server-test-suites"))

                api(libs.jetty.servlet)
            }
        }
    }
}
