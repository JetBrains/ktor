/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.test.*
import kotlin.test.Test

class CIOSpecificHttpsTest : TestWithKtor() {

    override val server: EmbeddedServer<*, *> = embeddedServer(
        Netty,
        applicationProperties {
            module {
                routing {
                    get("/") {
                        call.respondText("Hello, world")
                    }
                }
            }
        }
    ) {
        sslConnector(
            keyStore,
            "sha384ecdsa",
            { "changeit".toCharArray() },
            { "changeit".toCharArray() }
        ) {
            port = serverPort
            keyStorePath = keyStoreFile.absoluteFile
        }
    }

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        private lateinit var sslContext: SSLContext
        lateinit var x509TrustManager: X509TrustManager

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            keyStore = buildKeyStore {
                certificate("sha384ecdsa") {
                    hash = HashAlgorithm.SHA384
                    sign = SignatureAlgorithm.ECDSA
                    keySizeInBits = 384
                    password = "changeit"
                }
                certificate("sha256ecdsa") {
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.ECDSA
                    keySizeInBits = 256
                    password = "changeit"
                }
                certificate("sha384rsa") {
                    hash = HashAlgorithm.SHA384
                    sign = SignatureAlgorithm.RSA
                    keySizeInBits = 1024
                    password = "changeit"
                }
                certificate("sha1rsa") {
                    hash = HashAlgorithm.SHA1
                    sign = SignatureAlgorithm.RSA
                    keySizeInBits = 1024
                    password = "changeit"
                }
            }

            keyStore.saveToFile(keyStoreFile, "changeit")
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            x509TrustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
    }

    @Test
    fun hello() {
        CIOCipherSuites.SupportedSuites.forEach { suite ->
            /**
             * Outdated by jetty.
             */
            if (suite == CIOCipherSuites.ECDHE_ECDSA_AES128_SHA256) return@forEach

            /**
             * Too strong for old JDK.
             */
            if (suite == CIOCipherSuites.ECDHE_ECDSA_AES256_SHA384) return@forEach

            /**
             * Deprecated since jdk11.
             */
            if (suite == CIOCipherSuites.ECDHE_RSA_AES128_SHA256) return@forEach
            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES_128_GCM_SHA256) return@forEach

            if (suite == CIOCipherSuites.ECDHE_RSA_AES256_SHA384) return@forEach
            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES256_CBC_SHA) return@forEach
            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA) return@forEach

//            Mandatory
//            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA) return@forEach

            testWithEngine(CIO) {
                config {
                    engine {
                        https {
                            trustManager = x509TrustManager
                            cipherSuites = listOf(suite)
                        }
                    }
                }

                test { client ->
                    try {
                        println("Starting: ${suite.name}")
                        val actual = client.get("https://127.0.0.1:$serverPort/").body<String>()
                        assertEquals("Hello, world", actual)
                    } catch (cause: Throwable) {
                        println("${suite.name}: $cause")
                        client.cancel("Failed with: $cause")
                        fail("${suite.name}: $cause")
                    }
                }
            }
        }
    }

    @Ignore
    @Test
    fun testGetServerTrusted() {
        testWithEngine(CIO) {
            config {
                engine {
                    https {
                        trustManager = object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            }

                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            }

                            override fun getAcceptedIssuers(): Array<X509Certificate> {
                                return emptyArray()
                            }
                        }
                    }
                }
            }
            test { client ->
                assertEquals(
                    HttpStatusCode.MethodNotAllowed,
                    client.get("https://pt.api.sandbox.npay.eu/token/Grant").status
                )
            }
        }
    }
}
