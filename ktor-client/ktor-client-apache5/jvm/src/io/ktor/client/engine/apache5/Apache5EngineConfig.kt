/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.engine.*
import org.apache.hc.client5.http.config.*
import org.apache.hc.client5.http.impl.async.*
import javax.net.ssl.*

/**
 * A configuration for the [Apache5] client engine.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig)
 */
public class Apache5EngineConfig : HttpClientEngineConfig() {
    /**
     * Specifies whether to follow redirects automatically.
     * Disabled by default.
     *
     * _Note: By default, the Apache client allows `50` redirects._
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.followRedirects)
     */
    public var followRedirects: Boolean = false

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     */
    public var socketTimeout: Int = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     */
    public var connectTimeout: Long = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should start a request.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     */
    public var connectionRequestTimeout: Long = 20_000

    /**
     * Allows you to configure [SSL](https://ktor.io/docs/client-ssl.html) settings for this engine.
     */
    public var sslContext: SSLContext? = null

    internal var customRequest: (RequestConfig.Builder.() -> RequestConfig.Builder) = { this }

    internal var customClient: (HttpAsyncClientBuilder.() -> HttpAsyncClientBuilder) = { this }

    /**
     * Customizes a [RequestConfig.Builder] in the specified [block].
     */
    public fun customizeRequest(block: RequestConfig.Builder.() -> Unit) {
        val current = customRequest
        customRequest = { current(); block(); this }
    }

    /**
     * Customizes a [HttpAsyncClientBuilder] in the specified [block].
     */
    public fun customizeClient(block: HttpAsyncClientBuilder.() -> Unit) {
        val current = customClient
        customClient = { current(); block(); this }
    }
}
