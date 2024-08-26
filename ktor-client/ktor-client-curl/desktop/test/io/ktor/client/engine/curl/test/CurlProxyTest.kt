/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * This is a temporary tests that should be moved to the general test suite
 * once we support TLS options in client configs to connect to the local test TLS server.
 */
class CurlProxyTest {
    /**
     * Copied from ktor-client-tests
     */
    private val TEST_SERVER: String = "http://127.0.0.1:8080"

    /**
     * Proxy server url for tests.
     * Copied from ktor-client-tests
     */
    private val HTTP_PROXY_SERVER: String = "http://127.0.0.1:8082"

    @Test
    fun plainHttpTest() {
        val client = HttpClient(Curl) {
            engine {
                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        client.use {
            runBlocking {
                // replace with once moved to ktor-client-tests
//                assertEquals("Hello", client.get<String>("$TEST_SERVER/content/hello"))
                assertEquals("proxy", client.get("http://google.com/").body())
            }
        }
    }

    @Test
    fun httpsOverTunnelTestSecured() {
        val client = HttpClient(Curl) {
            engine {
                forceProxyTunneling = true
                sslVerify = true

                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        runBlocking {
            client.use {
                assertFailsWith<IllegalStateException> {
                    client.get("https://localhost:8089/")
                }
            }
        }
    }

    @Test
    fun httpsOverTunnelTest() {
        val client = HttpClient(Curl) {
            engine {
                forceProxyTunneling = true
                sslVerify = false

                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        runBlocking {
            client.use {
                client.get("https://localhost:8089/").let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("Hello, TLS!", response.bodyAsText())
                    assertEquals("text/plain;charset=utf-8", response.headers[HttpHeaders.ContentType])
                    assertEquals("TLS test server", response.headers["X-Comment"])
                }
            }
        }
    }
}
