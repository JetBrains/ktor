/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

kotlin.sourceSets {
    jvmMain {
        dependencies {
            compileOnly(libs.slf4j.simple)
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
        }
    }
    jvmMain {
        dependencies {
            api(libs.kotlinx.coroutines.slf4j)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-jackson"))
        }
    }
}
