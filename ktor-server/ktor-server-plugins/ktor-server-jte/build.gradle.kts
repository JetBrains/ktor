/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

ktorBuild {
    // The minimal JDK version required for jte 3.0+
    jvmToolchain(17)
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(libs.jte)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api(libs.jte.kotlin)
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
        }
    }
}
