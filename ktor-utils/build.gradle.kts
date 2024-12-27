/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

plugins {
    id("kotlinx-serialization")
}

kotlin {
    createCInterop(
        "threadUtils",
        sourceSet = "nix",
        definitionFilePath = { target ->
            // We create an empty cinterop for androidNative targets
            // to overcome an issue with cinterop Gradle dependencies resolution
            when {
                target.startsWith("androidNative") -> "androidNative/interop/threadUtils.def"
                else -> "nix/interop/threadUtils.def"
            }
        }
    )

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-io"))
                api(libs.kotlinx.serialization.core)
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
            }
        }
    }
}
