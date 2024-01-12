/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.tomcat.jakarta

import io.ktor.client.statement.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.jakarta.*
import io.ktor.server.testing.suites.*
import io.ktor.server.tomcat.jakarta.*
import jakarta.servlet.*
import jakarta.servlet.Filter
import org.apache.catalina.core.*
import org.apache.tomcat.util.descriptor.web.*
import java.io.*
import java.util.logging.*
import kotlin.test.*

class TomcatCompressionTest :
    CompressionTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }
}

class TomcatContentTest : ContentTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    /**
     * Tomcat 9.0.56 issue
     */
    @Ignore
    override fun testMultipartFileUpload() {
    }

    @Ignore
    override fun testMultipartFileUploadLarge() {
    }
}

class TomcatHttpServerCommonTest :
    HttpServerCommonTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
    }

    @Ignore // KTOR-6480
    override fun testErrorInBodyClosesConnectionWithContentLength() {}
}

class TomcatHttpServerJvmTest :
    HttpServerJvmTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    @Ignore
    @Test
    override fun testUpgrade() {
        super.testUpgrade()
    }

    @Test
    fun testServletAttributes() {
        createAndStartServer {
            get("/tomcat/attributes") {
                call.respondText(
                    call.request.servletRequestAttributes["ktor.test.attribute"]?.toString() ?: "Not found"
                )
            }
        }

        withUrl("/tomcat/attributes") {
            assertEquals("135", call.response.bodyAsText())
        }
    }

    override fun configure(configuration: TomcatApplicationEngine.Configuration) {
        super.configure(configuration)
        configuration.configureTomcat = {
            addAttributesFilter()
        }
    }

    private fun org.apache.catalina.startup.Tomcat.addAttributesFilter() {
        server.addLifecycleListener {
            host.findChildren().forEach {
                if (it is StandardContext) {
                    if (it.findFilterConfig("AttributeFilter") == null) {
                        it.addFilterDef(
                            FilterDef().apply {
                                filterName = "AttributeFilter"
                                filterClass = AttributeFilter::class.java.name
                                filter = AttributeFilter()
                            }
                        )
                        it.addFilterMap(
                            FilterMap().apply {
                                addURLPattern("/*")
                                filterName = "AttributeFilter"
                            }
                        )
                    }
                }
            }
        }
    }

    internal class AttributeFilter : Filter {
        override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
            request.setAttribute("ktor.test.attribute", "135")
            chain.doFilter(request, response)
        }
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class TomcatSustainabilityTestSuite :
    SustainabilityTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    /**
     * Tomcat trim `vspace` symbol and drop content-length. The request is treated as chunked.
     */
    @Ignore
    @Test
    override fun testChunkedWithVSpace() {
        super.testChunkedWithVSpace()
    }

    @Ignore
    @Test
    override fun testBlockingConcurrency() {
        super.testBlockingConcurrency()
    }
}

class TomcatConfigTest : ConfigTestSuite(Tomcat)

class TomcatConnectionTest : ConnectionTestSuite(Tomcat)

class TomcatClientCertTest :
    ClientCertTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {

    override fun sslConnectorBuilder(): EngineSSLConnectorBuilder {
        val serverKeyStorePath = File.createTempFile("serverKeys", "jks")

        return EngineSSLConnectorBuilder(
            keyAlias = "mykey",
            keyStore = ca.generateCertificate(file = serverKeyStorePath, keyType = KeyType.Server),
            keyStorePassword = { "changeit".toCharArray() },
            privateKeyPassword = { "changeit".toCharArray() },
        ).apply {
            keyStorePath = serverKeyStorePath
            port = 0

            val trustStorePath = File.createTempFile("trustStore", "jks")
            trustStore = ca.trustStore(trustStorePath)
            this.trustStorePath = trustStorePath
        }
    }
}

class TomcatServerPluginsTest :
    ServerPluginsTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}
