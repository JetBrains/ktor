description = "Ktor network utilities"

kotlin {
    createCInterop("network", nixTargets()) {
        definitionFile = projectDir.resolve("nix/interop/network.def")
    }

    sourceSets {
        jvmAndPosixMain {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        jvmAndPosixTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-junit"))
                implementation(libs.mockk)
            }
        }

        macosTest {
            val nixTest = getByName("posixTest")
            dependsOn(nixTest)
        }
        watchosTest {
            val nixTest = getByName("posixTest")
            dependsOn(nixTest)
        }
        tvosTest {
            val nixTest = getByName("posixTest")
            dependsOn(nixTest)
        }
        iosTest {
            val nixTest = getByName("posixTest")
            dependsOn(nixTest)
        }
    }
}
