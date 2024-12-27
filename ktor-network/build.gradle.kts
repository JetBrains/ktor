/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

description = "Ktor network utilities"

kotlin {
    createCInterop("network", sourceSet = "nix")
    createCInterop("un", sourceSet = "androidNative")
    createCInterop("un", sourceSet = "darwin")
    createCInterop("afunix", sourceSet = "windows")

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-test-base"))
                implementation(libs.mockk)
            }
        }
    }
}
