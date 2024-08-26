/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.slf4j.*
import org.slf4j.*
import kotlin.test.*

private class LoggerWithMdc : Logger {
    val logs = mutableListOf<Pair<String, String>>()

    override fun log(message: String) {
        val contextMap = MDC.getCopyOfContextMap()
        val mdcText = contextMap?.let {
            it.entries.sortedBy { it.key }.joinToString(prefix = "[", postfix = "]") { "${it.key}=${it.value}" }
        } ?: ""
        logs += message to mdcText
    }
}

class LoggingTestJvm : ClientLoader(timeoutSeconds = 1000000) {

    @OptIn(InternalAPI::class)
    @Test
    fun testMdc() = clientTests(listOf("native:CIO")) {
        val testLogger = LoggerWithMdc()

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            MDC.put("mdc", "value")
            withContext(MDCContext()) {
                client.request {
                    method = HttpMethod.Post
                    val array = "test".toByteArray()
                    setBody(ByteReadChannel(array))
                    url("$TEST_SERVER/content/echo")
                }
            }
        }

        after {
            assertTrue(testLogger.logs.isNotEmpty())
            testLogger.logs.forEach {
                assertEquals(
                    "[mdc=value]",
                    it.second,
                    "MDC context is not preserved in $it message (${it.second}): ${it.first}"
                )
            }
        }
    }
}
