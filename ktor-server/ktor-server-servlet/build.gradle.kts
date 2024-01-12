description = ""

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            compileOnly(libs.javax.servlet)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(project(":ktor-server:ktor-server-config-yaml"))
            implementation(libs.mockk)
            implementation(libs.javax.servlet)
        }
    }
}
