import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val nativeTargets: List<KotlinNativeTarget> by extra
kotlin {
    nativeTargets.forEach {
        it.compilations {
            val main by getting {
                cinterops {
                    val bits by creating { defFile = file("posix/interop/bits.def") }
                    // val sockets by creating { defFile = file("posix/interop/sockets.def") }
                }
            }
            val test by getting {
                cinterops {
                    val testSockets by creating { defFile = file("posix/interop/testSockets.def") }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

// Hack: register the Native interop klibs as outputs of Kotlin source sets:
        val bitsMain by creating { dependsOn(commonMain) }
        // val socketsMain by creating { dependsOn(commonMain) }

        val posixMain by getting {
            dependsOn(bitsMain)
            // dependsOn(socketsMain)
        }

        val ss = listOf(
            "macosX64",
            "linuxX64",
            "iosX64",
            "iosArm64",
            "iosArm32",
            "tvosArm64",
            "tvosX64",
            "watchosArm32",
            "watchosArm64",
            "watchosX86",
            "watchosX64",
        ).map { "${it}Main" }.map { getByName(it) }
        val posixWoMingw by creating {
            ss.forEach { it.dependsOn(this) }
            dependsOn(posixMain)
        }

        val b32 by creating {
            listOf(
                "iosArm32",
                "watchosArm32",
                "watchosX86",
                "watchosArm64",
            ).map { getByName("${it}Main") }.forEach {
                it.dependsOn(this)
                dependsOn(posixWoMingw)
            }
        }

        val b64 by creating {
            listOf(
                "macosX64",
                "linuxX64",
                "iosX64",
                "iosArm64",
                "tvosArm64",
                "tvosX64",
//                "mingwX64",
//                "watchosArm64",
                "watchosX64",
            ).map { getByName("${it}Main") }.forEach {
                it.dependsOn(this)
                dependsOn(posixWoMingw)
            }
        }

    }
}
