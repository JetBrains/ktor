description = ""

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-servlet-jakarta"))
            api(libs.tomcat.catalina.jakarta)
            api(libs.tomcat.embed.core.jakarta)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-base"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.logback.classic)
        }
    }
}
