/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.test.*

/**
 * Helper interface to test client.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.tests.utils.ClientLoader)
 */
expect abstract class ClientLoader(timeoutSeconds: Int = 60) {
    /**
     * Perform test against all clients from dependencies.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.tests.utils.ClientLoader.clientTests)
     */
    fun clientTests(
        skipEngines: List<String> = emptyList(),
        onlyWithEngine: String? = null,
        retries: Int = 1,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult

    /**
     * Print coroutines in debug mode.
     */
    fun dumpCoroutines()
}
