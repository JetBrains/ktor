description = ""

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-network"))
            api(project(":ktor-network:ktor-network-tls"))
        }
    }
    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-server:ktor-server-test-suites"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.logback.classic)
        }
    }
}
