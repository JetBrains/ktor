apply<test.server.TestServerPlugin>()

val paths = listOf(
    "/opt/homebrew/opt/curl/include/",
    "/opt/local/include/",
    "/usr/local/include/",
    "/usr/include/",
    "/usr/local/opt/curl/include/",
    "/usr/include/x86_64-linux-gnu/",
    "/usr/local/Cellar/curl/7.62.0/include/",
    "/usr/local/Cellar/curl/7.63.0/include/",
    "/usr/local/Cellar/curl/7.65.3/include/",
    "/usr/local/Cellar/curl/7.66.0/include/",
    "/usr/local/Cellar/curl/7.80.0/include/",
    "/usr/local/Cellar/curl/7.80.0_1/include/",
    "/usr/local/Cellar/curl/7.81.0/include/",
    "desktop/interop/mingwX64/include/",
)

plugins {
    id("kotlinx-serialization")
}

kotlin {
    if (fastTarget()) return@kotlin

    createCInterop("libcurl", listOf("macosX64", "linuxX64", "mingwX64")) {
        defFile = File(projectDir, "desktop/interop/libcurl.def")
        includeDirs.headerFilterOnly(paths)
    }

    createCInterop("libcurl", listOf("macosArm64")) {
        defFile = File(projectDir, "desktop/interop/libcurl_arm64.def")
        includeDirs.headerFilterOnly(paths)
    }

    createCInterop("libcurl", listOf("linuxArm64")) {
        defFile = File(projectDir, "desktop/interop/libcurl_linux_arm64.def")
        includeDirs.headerFilterOnly(listOf("desktop/interop/linuxArm64/include/"))
    }

    sourceSets {
        desktopMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        desktopTest {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}
