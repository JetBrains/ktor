description = "This module is deprecated. All the contents are moved to `ktor-server-core`."

kotlin {
    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
            }
        }
    }
}
