description = "Ktor JSON Content Negotiation via kotlinx.serialization support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.kotlinx.serialization.json)
        }
    }
    jvmTest {
        @Suppress("ktlint:standard:max-line-length")
        dependencies {
            api(
                project(
                    ":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"
                )
            )
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-tests"))
        }
    }
}
