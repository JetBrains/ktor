package io.ktor.tests.server.tomcat

import io.ktor.server.testing.*
import io.ktor.server.tomcat.*
import java.util.logging.*

class TomcatHostTest : HostTestSuite<TomcatApplicationHost, TomcatApplicationHost.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }
}