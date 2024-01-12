
apply<test.server.TestServerPlugin>()

plugins {
    id("kotlinx-serialization")
}

kotlin {
    if (fastTarget()) return@kotlin

    createCInterop("winhttp", windowsTargets()) {
        defFile = File(projectDir, "windows/interop/winhttp.def")
    }

    sourceSets {
        windowsMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        windowsTest {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}
