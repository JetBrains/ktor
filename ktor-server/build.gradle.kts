description = "Wrapper for ktor-server-core and base plugins"

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-default-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
        }
    }
    commonMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-caching-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-id"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-cors"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-csrf"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-data-conversion"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-double-receive"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-hsts"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-http-redirect"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-method-override"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-sessions"))
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}
