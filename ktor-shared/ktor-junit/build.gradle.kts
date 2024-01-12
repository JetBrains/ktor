/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common extensions for JUnit 5 testing framework"

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(kotlin("test"))
            api(kotlin("test-junit5"))
        }
    }
}
