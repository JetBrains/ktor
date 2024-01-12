description = ""
kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.dropwizard.core)
                api(libs.dropwizard.jvm)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-cors"))
                api(project(":ktor-shared:ktor-junit"))
            }
        }
    }
}
