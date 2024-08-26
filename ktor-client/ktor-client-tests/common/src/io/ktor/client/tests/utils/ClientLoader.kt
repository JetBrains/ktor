/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.test.*

/**
 * Helper interface to test client.
 */
expect abstract class ClientLoader(timeoutSeconds: Int = 60) {
    /**
     * Perform test against all clients from dependencies.
     */
    fun clientTests(
        skipEngines: List<String> = emptyList(),
        onlyWithEngine: String? = null,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult

    /**
     * Print coroutines in debug mode.
     */
    fun dumpCoroutines()
}
