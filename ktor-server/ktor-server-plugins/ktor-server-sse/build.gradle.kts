description = "Server-sent events (SSE) support"

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-shared:ktor-sse"))
        }
    }
}
