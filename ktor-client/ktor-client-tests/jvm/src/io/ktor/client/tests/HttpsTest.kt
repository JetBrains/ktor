/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.errors.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.test.*

abstract class HttpsTest<T : HttpClientEngineConfig>(
    private val factory: HttpClientEngineFactory<T>
) : TestWithKtor() {

    override val server = embeddedServer(Netty, serverPort) {}

    protected val trustAllCertificates = arrayOf<X509TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
    )

    protected val unsafeSslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCertificates, SecureRandom())
    }

    protected abstract fun T.disableCertificatePinning()

    @Test
    fun testHttpsOverProxy() = testWithEngine(factory) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
                disableCertificatePinning()
            }
        }

        test { client ->
            val first = client.get("https://localhost:8089/")
            assertEquals("Hello, TLS!", first.body())
            assertEquals("TLS test server", first.headers["X-Comment"])
        }
    }

    @Test
    fun `test hostname is verified`() = testWithEngine(factory) {
        test { client ->
            assertFailsWith<IOException> {
                client.get("https://wrong.host.badssl.com/")
            }
        }
    }
}
