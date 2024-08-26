description = ""

kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-network"))
        }
    }
    jvmAndPosixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-server:ktor-server-test-suites"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-base"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.logback.classic)
        }
    }
}
