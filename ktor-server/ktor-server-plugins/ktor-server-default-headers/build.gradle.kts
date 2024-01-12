/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(libs.kotlinx.datetime)
            }
        }
    }

    createCInterop("utils", nixTargets()) {
        defFile = File(projectDir, "nix/interop/utils.def")
    }
}

val configuredVersion: String by rootProject.extra

val writeKtorVersionDefFile by tasks.registering {
    File(projectDir, "nix/interop/utils.def").apply {
        parentFile.mkdirs()
        createNewFile()
    }.writer().buffered().use { writer ->
        writer.write(
            """
            ---
            
            char* read_ktor_version() {
                return "$configuredVersion"; 
            }
            """.trimIndent()
        )
    }
}

tasks.filter { it.name.startsWith("cinteropUtils") }.forEach {
    it.dependsOn(writeKtorVersionDefFile)
}
