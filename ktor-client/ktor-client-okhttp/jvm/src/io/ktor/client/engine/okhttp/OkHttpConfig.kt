/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.engine.*
import okhttp3.*

/**
 * A configuration for the [OkHttp] client engine.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.okhttp.OkHttpConfig)
 */
public class OkHttpConfig : HttpClientEngineConfig() {

    internal var config: OkHttpClient.Builder.() -> Unit = {
        followRedirects(false)
        followSslRedirects(false)

        retryOnConnectionFailure(true)
    }

    /**
     * Allows you to specify a preconfigured [OkHttpClient] instance.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.okhttp.OkHttpConfig.preconfigured)
     */
    public var preconfigured: OkHttpClient? = null

    /**
     * Specifies the size of cache that keeps recently used [OkHttpClient] instances.
     * Set this property to `0` to disable caching.
     */
    public var clientCacheSize: Int = 10

    /**
     * Specifies the [WebSocket.Factory] used to create a [WebSocket] instance.
     * Otherwise, [OkHttpClient] is used directly.
     */
    public var webSocketFactory: WebSocket.Factory? = null

    /**
     * Configures [OkHttpClient] using [OkHttpClient.Builder].
     */
    public fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }

    /**
     * Adds an [Interceptor] to the [OkHttp] client.
     */
    public fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    /**
     * Adds a network [Interceptor] to the [OkHttp] client.
     */
    public fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
}
