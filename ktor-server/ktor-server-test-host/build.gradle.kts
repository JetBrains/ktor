/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-test-dispatcher"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network:ktor-network-tls"))

            api(project(":ktor-client:ktor-client-apache"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn"t increase the size of the final artifact.
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))

            api(libs.kotlin.test)
            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(project(":ktor-server:ktor-server-config-yaml"))
            api(libs.kotlin.test)
        }
    }
}
