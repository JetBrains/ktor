/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

ktorBuild {
    // The minimal JVM version required for Tomcat 10
    jvmToolchain(11)
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-servlet-jakarta"))
            api(libs.tomcat.catalina.jakarta)
            api(libs.tomcat.embed.core.jakarta)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-base"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))
        }
    }
}
