/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

subprojects {
    kotlin {
        sourceSets {
            commonMain {
                dependencies {
                    api(project(":ktor-server:ktor-server-core"))
                }
            }
            commonTest {
                dependencies {
                    api(project(":ktor-server:ktor-server-test-host"))
                }
            }

            jvmTest {
                dependencies {
                    implementation(project(":ktor-server:ktor-server-test-base"))
                }
            }
        }
    }
}
