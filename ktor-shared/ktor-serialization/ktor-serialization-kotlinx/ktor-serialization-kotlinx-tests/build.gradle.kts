plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(kotlin("test"))
            api(kotlin("test-annotations-common"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-client:ktor-client-tests"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
    jvmMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-tests"))

            api(libs.logback.classic)
        }
    }
}
