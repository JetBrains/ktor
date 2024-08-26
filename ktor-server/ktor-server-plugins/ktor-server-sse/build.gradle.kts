description = "Server-sent events (SSE) support"

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-sse"))
        }
    }
}
