description = "Jetty based client engine"

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))

            api(libs.jetty.http2.client.jakarta)
            api(libs.jetty.alpn.java.client.jakarta)
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
