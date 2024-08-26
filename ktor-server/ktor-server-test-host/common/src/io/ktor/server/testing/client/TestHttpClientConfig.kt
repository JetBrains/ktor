/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.server.testing.*

public class TestHttpClientConfig : HttpClientEngineConfig() {
    public lateinit var app: TestApplicationEngine
}
