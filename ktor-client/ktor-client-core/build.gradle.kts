description = "Ktor http client"

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-shared:ktor-events"))
            api(project(":ktor-shared:ktor-websocket-serialization"))
            api(project(":ktor-shared:ktor-sse"))
        }
    }

    jvmMain {
        dependencies {
            implementation(libs.kotlinx.coroutines.slf4j)
        }
    }

    jsMain {
        dependencies {
            api(npm("node-fetch", libs.versions.node.fetch.version.get()))
            api(npm("abort-controller", libs.versions.abort.controller.version.get()))
            api(npm("ws", libs.versions.ws.version.get()))
        }
    }

    wasmJsMain {
        dependencies {
            api(npm("node-fetch", libs.versions.node.fetch.version.get()))
            api(npm("abort-controller", libs.versions.abort.controller.version.get()))
            api(npm("ws", libs.versions.ws.version.get()))
        }
    }

    commonTest {
        dependencies {
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client:ktor-client-mock"))
        }
    }
}
