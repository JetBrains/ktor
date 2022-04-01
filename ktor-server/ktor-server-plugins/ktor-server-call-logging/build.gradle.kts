/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""
val jansi_version: String by project.extra
val coroutines_version: String by project

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.jansi)
                implementation(libs.kotlinx.coroutines.slf4j)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-call-id"))
            }
        }
    }
}
