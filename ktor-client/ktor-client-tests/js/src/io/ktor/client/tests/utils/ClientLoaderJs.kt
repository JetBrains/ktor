// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

/**
 * Helper interface to test client.
 */
actual abstract class ClientLoader actual constructor(private val timeoutSeconds: Int) {
    /**
     * Perform test against all clients from dependencies.
     */
    @OptIn(DelicateCoroutinesApi::class)
    actual fun clientTests(
        skipEngines: List<String>,
        onlyWithEngine: String?,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult {
        val skipEnginesLowerCase = skipEngines.map { it.lowercase() }
        return if ((onlyWithEngine != null && onlyWithEngine != "js") || skipEnginesLowerCase.contains("js")) {
            GlobalScope.async {}.asPromise()
        } else {
            testWithEngine(Js) {
                withTimeout(timeoutSeconds.toLong() * 1000) {
                    block()
                }
            }
        }
    }

    actual fun dumpCoroutines() {
        error("Debug probes unsupported[js]")
    }
}
