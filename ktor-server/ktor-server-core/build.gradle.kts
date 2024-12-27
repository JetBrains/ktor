/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

description = ""

kotlin {
    createCInterop("host_common", sourceSet = "posix")

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-utils"))
                api(project(":ktor-http"))
                api(project(":ktor-shared:ktor-serialization"))
                api(project(":ktor-shared:ktor-events"))
                api(project(":ktor-http:ktor-http-cio"))
                api(project(":ktor-shared:ktor-websockets"))

                api(libs.kotlin.reflect)
            }
        }

        jvmMain {
            dependencies {
                api(libs.typesafe.config)
                implementation(libs.jansi)
            }
        }

        commonTest {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-server:ktor-server-config-yaml"))
                implementation(project(":ktor-server:ktor-server-test-base"))
                implementation(project(":ktor-server:ktor-server-test-suites"))

                implementation(libs.mockk)
            }
        }
    }
}
