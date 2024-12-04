/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.util.ssl.*

/**
 * A configuration for the [Jetty] client engine.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.jetty.JettyEngineConfig)
 */
public class JettyEngineConfig : HttpClientEngineConfig() {
    internal var config: (HTTP2Client) -> Unit = {}

    /**
     * Allows you to configure [SSL](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.jetty.JettyEngineConfig.sslContextFactory)
     */
    public var sslContextFactory: SslContextFactory = SslContextFactory.Client()

    /**
     * Specifies the size of cache that keeps recently used [JettyHttp2Engine] instances.
     */
    public var clientCacheSize: Int = 10

    /**
     * Configures a raw Jetty client.
     */
    public fun configureClient(block: (HTTP2Client) -> Unit) {
        val current = config
        config = {
            current(it)
            block(it)
        }
    }
}
