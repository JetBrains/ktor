/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

ktorBuild {
    jvmToolchain(11)
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))

            compileOnly(libs.jakarta.servlet)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(project(":ktor-server:ktor-server-config-yaml"))
            implementation(libs.mockk)
            implementation(libs.jakarta.servlet)
        }
    }
}
