/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client CallId support"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-shared:ktor-call-id"))
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-call-id"))
            }
        }
    }
}

// tests need server, so can't be run in browser
tasks.named("jsBrowserTest") { onlyIf { false } }
tasks.named("wasmJsBrowserTest") { onlyIf { false } }
