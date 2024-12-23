/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

ktorBuild {
    // The minimal JDK version required for Swagger Codegen
    jvmToolchain(11)
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))

                implementation(libs.swagger.codegen)
                implementation(libs.swagger.codegen.generators)
                implementation(libs.swagger.parser)
            }
        }
    }
}
