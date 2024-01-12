description = ""

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))

            compileOnly(libs.jakarta.servlet)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(project(":ktor-server:ktor-server-config-yaml"))
            implementation(libs.mockk)
            implementation(libs.jakarta.servlet)
        }
    }
}
