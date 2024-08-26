description = ""

val jetty_alpn_boot_version: String? by extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(kotlin("test"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network:ktor-network-tls"))

            api(project(":ktor-client:ktor-client-apache"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))
            api(project(":ktor-shared:ktor-junit"))

            if (jetty_alpn_boot_version != null) {
                api(libs.jetty.alpn.boot)
            }

            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }
}
