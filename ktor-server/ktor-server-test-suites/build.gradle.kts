description = ""

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(kotlin("test"))

            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            implementation(project(":ktor-server:ktor-server-test-host"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-hsts"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))
        }
    }

    jvmMain {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-default-headers"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-request-validation"))

            implementation(kotlin("test-junit5"))

            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

            api(libs.logback.classic)
        }
    }
}
