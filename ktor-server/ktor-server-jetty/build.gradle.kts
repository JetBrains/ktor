description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-servlet"))
                api(libs.jetty.server)
                api(libs.jetty.servlets)
                api(libs.jetty.alpn.server)
                api(libs.jetty.alpn.java.server)
                api(libs.jetty.alpn.openjdk8.server)
                api(libs.jetty.http2.server)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-base"))
                api(project(":ktor-server:ktor-server-test-suites"))

                api(libs.jetty.servlet)
                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
                api(libs.logback.classic)
            }
        }
    }
}

val jetty_alpn_boot_version: String? by extra

dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", libs.jetty.alpn.boot)
    }
}
