description = ""

val jetty_alpn_boot_version: String? by extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-test-dispatcher"))
        }
    }

    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network:ktor-network-tls"))

            api(project(":ktor-client:ktor-client-apache"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn"t increase the size of the final artifact.
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))

            if (jetty_alpn_boot_version != null) {
                api(libs.jetty.alpn.boot)
            }

            api(kotlin("test"))
            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(project(":ktor-server:ktor-server-config-yaml"))
            api(kotlin("test"))
        }
    }
}
