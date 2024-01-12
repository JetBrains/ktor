description = ""

val jetty_alpn_api_version: String by extra

val enableAlpnProp = project.hasProperty("enableAlpn")
val osName = System.getProperty("os.name").lowercase()
val nativeClassifier: String? = if (enableAlpnProp) {
    when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("linux") -> "linux-x86_64"
        osName.contains("mac") -> "osx-x86_64"
        else -> throw InvalidUserDataException("Unsupported os family $osName")
    }
} else {
    null
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))

            api(libs.netty.codec.http2)
            api(libs.jetty.alpn.api)

            api(libs.netty.transport.native.kqueue)
            api(libs.netty.transport.native.epoll)
            if (nativeClassifier != null) {
                api(libs.netty.tcnative.boringssl.static)
            }
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))

            api(libs.netty.tcnative)
            api(libs.netty.tcnative.boringssl.static)
            api(libs.mockk)
            api(libs.logback.classic)

            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}

val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks
jvmTest.apply {
    systemProperty("enable.http2", "true")
}
