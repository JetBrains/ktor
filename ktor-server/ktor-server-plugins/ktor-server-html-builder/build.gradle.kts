kotlin.sourceSets {
    commonMain {
        dependencies {
            api(libs.kotlinx.html)
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
        }
    }
}
