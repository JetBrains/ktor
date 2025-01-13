/*
* Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(libs.micrometer)
            implementation(project(":ktor-client:ktor-client-core"))
        }
    }
    jvmTest {
        dependencies{
        }
    }
}
