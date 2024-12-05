/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-network"))
            api(project(":ktor-utils"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-shared:ktor-junit"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(libs.netty.handler)
            api(libs.mockk)
        }
    }
}
