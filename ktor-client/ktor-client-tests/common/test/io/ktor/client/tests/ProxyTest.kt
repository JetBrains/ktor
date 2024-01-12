/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
data class ProxyResponse(val status: String)

class ProxyTest : ClientLoader() {

    @Test
    fun testHttpProxy() = clientTests(listOf("Js")) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
            }
        }

        test { client ->
            val response = client.get("http://google.com").body<String>()
            assertEquals("proxy", response)
        }
    }

    @Test
    fun testProxyWithSerialization() = clientTests(listOf("Js")) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
            }

            install(ContentNegotiation) { json() }
        }

        test { client ->
            val response = client.get("http://google.com/json").body<ProxyResponse>()
            val expected = ProxyResponse("ok")

            assertEquals(expected, response)
        }
    }
}
