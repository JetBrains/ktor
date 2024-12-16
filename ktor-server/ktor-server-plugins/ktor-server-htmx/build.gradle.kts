kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-htmx"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))
        }
    }
}
