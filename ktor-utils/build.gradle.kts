kotlin {
    createCInterop("threadUtils", nixTargets()) {
        definitionFile = File(projectDir, "nix/interop/threadUtils.def")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-io"))
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-junit"))
            }
        }
    }
}
