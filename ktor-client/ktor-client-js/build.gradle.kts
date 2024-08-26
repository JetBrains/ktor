kotlin {
    sourceSets {
        jsMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }

        wasmJsMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
    }
}
