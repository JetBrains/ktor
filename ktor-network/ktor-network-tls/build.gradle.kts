kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-network"))
            api(project(":ktor-utils"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(libs.netty.handler)
            api(libs.mockk)
        }
    }
}
