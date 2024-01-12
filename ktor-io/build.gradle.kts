kotlin {
    createCInterop("mutex", posixTargets()) {
        defFile = File(projectDir, "posix/interop/mutex.def")
    }

    sourceSets {
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
