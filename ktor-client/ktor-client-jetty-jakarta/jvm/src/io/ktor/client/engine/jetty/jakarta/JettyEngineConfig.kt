/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.engine.*
import org.eclipse.jetty.http2.client.HTTP2Client
import org.eclipse.jetty.util.ssl.SslContextFactory

/**
 * A configuration for the [Jetty] client engine.
 */
public class JettyEngineConfig : HttpClientEngineConfig() {
    internal var config: (HTTP2Client) -> Unit = {}

    /**
     * Allows you to configure [SSL](https://ktor.io/docs/client-ssl.html) settings for this engine.
     */
    public var sslContextFactory: SslContextFactory.Client = SslContextFactory.Client()

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
