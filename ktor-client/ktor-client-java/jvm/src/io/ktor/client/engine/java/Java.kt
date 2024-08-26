/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM client engine that uses the Java HTTP Client introduced in Java 11.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Java)
 * ```
 * To configure the engine, pass settings exposed by [JavaHttpConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Java) {
 *     engine {
 *         // this: JavaHttpConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public object Java : HttpClientEngineFactory<JavaHttpConfig> {
    override fun create(block: JavaHttpConfig.() -> Unit): HttpClientEngine =
        JavaHttpEngine(JavaHttpConfig().apply(block))
}

public class JavaHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Java

    override fun toString(): String = "Java"
}
