/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.*
import io.ktor.client.tests.utils.*
import io.ktor.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class ClientEngineTest<T : HttpClientEngineConfig>(private val factory: HttpClientEngineFactory<T>) {

    /** Perform test against the client specified in the test constructor. */
    fun testClient(
        timeout: Duration = 1.minutes,
        retries: Int = DEFAULT_RETRIES,
        test: suspend TestClientBuilder<T>.() -> Unit
    ) = testWithEngine(factory, timeout = timeout, retries = retries, block = test)
}
