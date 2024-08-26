
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

kotlin {
    createCInterop("mutex", posixTargets()) {
        definitionFile = File(projectDir, "posix/interop/mutex.def")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.io.core)
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
