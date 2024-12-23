/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-servlet-jakarta"))
                api(libs.jetty.server.jakarta)
                api(libs.jetty.servlets.jakarta)
                api(libs.jakarta.servlet)
                api(libs.jetty.alpn.server.jakarta)
                api(libs.jetty.alpn.java.server.jakarta)
                api(libs.jetty.alpn.openjdk8.server)
                api(libs.jetty.http2.server.jakarta)
            }
        }
        jvmTest {
            dependencies {
                api(libs.kotlin.test.junit5)
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-base"))
                api(project(":ktor-server:ktor-server-test-suites"))

                api(libs.jetty.servlet.jakarta)
                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
                api(libs.logback.classic)
            }
        }
    }
}

val jetty_alpn_boot_version: String? by extra

dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", libs.jetty.alpn.boot)
    }
}
