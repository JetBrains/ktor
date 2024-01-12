/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.utils.io.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies.
 * https://ktor.io/docs/http-client-engines.html
 */
@OptIn(InternalAPI::class)
@KtorDsl
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = engines.firstOrNull()?.let { HttpClient(it, block) } ?: error(
    "Failed to find HttpClientEngineContainer. Consider adding [HttpClientEngine] implementation in dependencies."
)
