apply<test.server.TestServerPlugin>()

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-network:ktor-network-tls"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
        }
    }
}
// pass JVM option to enlarge built-in HttpUrlConnection pool
// to avoid failures due to lack of local socket ports
val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks

jvmTest.apply {
    useJUnitPlatform()

    jvmArgs("-Dhttp.maxConnections=32")
}
