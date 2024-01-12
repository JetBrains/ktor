
kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))

            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
            implementation(libs.kotlin.reflect)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-gson"))
        }
    }
}
