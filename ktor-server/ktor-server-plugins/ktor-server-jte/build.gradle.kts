/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin.sourceSets {
    val jteVersion = "2.3.2"
    jvmMain {
        dependencies {
            api("gg.jte:jte:$jteVersion")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api("gg.jte:jte-kotlin:$jteVersion")
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
        }
    }
}
