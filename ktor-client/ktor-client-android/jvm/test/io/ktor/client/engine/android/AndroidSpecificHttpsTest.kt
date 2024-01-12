/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeAll
import java.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.test.*
import kotlin.test.Test

class AndroidSpecificHttpsTest : TestWithKtor() {
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
        sslConnector(keyStore, "sha256ecdsa", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
            port = serverPort
            keyStorePath = keyStoreFile.absoluteFile
        }
    }

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslContext: SSLContext
        private lateinit var x509TrustManager: X509TrustManager

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
    fun hello(): Unit = runBlocking {
        HttpClient(
            Android.config {
                sslManager = { conn ->
                    conn.sslSocketFactory = sslContext.socketFactory
                }
            }
        ).use { client ->
            val actual = client.get("https://127.0.0.1:$serverPort/").body<String>()
            assertEquals("Hello, world", actual)
        }
    }
}
