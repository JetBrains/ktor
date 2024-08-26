description = ""

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-shared:ktor-websocket-serialization"))
        }
    }

    commonTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-websockets"))
        }
    }
}
