/*
* Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.winhttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * This is a temporary tests that should be moved to the general test suite
 * once we support TLS options in client configs to connect to the local test TLS server.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.winhttp.WinHttpProxyTest)
 */
class WinHttpProxyTest {
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
        val client = HttpClient(WinHttp) {
            engine {
                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        client.use {
            runBlocking {
                // replace with once moved to ktor-client-tests
                assertEquals("hello", client.get("$TEST_SERVER/content/hello").body())
                assertEquals("proxy", client.get("http://google.com/").body())
            }
        }
    }
}
