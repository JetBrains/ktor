
kotlin.sourceSets {
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(libs.jetty.servlet.jakarta)
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-jetty-jakarta"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

            api(libs.logback.classic)
        }
    }
}

val jetty_alpn_boot_version: String? by extra
dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", libs.jetty.alpn.boot)
    }
}

val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks
jvmTest.apply {
    useJUnitPlatform()

    systemProperty("enable.http2", "true")
    exclude("**/*StressTest*")

    if (jetty_alpn_boot_version != null && JavaVersion.current() == JavaVersion.VERSION_1_8) {
        val bootClasspath = configurations.named("boot").get().files
        jvmArgs(bootClasspath.map { "-Xbootclasspath/p:${it.absolutePath}" }.iterator())
    }
}
