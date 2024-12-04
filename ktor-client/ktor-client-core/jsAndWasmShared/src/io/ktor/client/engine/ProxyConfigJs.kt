/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.ProxyConfig)
 */
public actual class ProxyConfig

/**
 * [ProxyConfig] factory.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.ProxyBuilder)
 */
public actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.ProxyBuilder.http)
     */
    public actual fun http(url: Url): ProxyConfig {
        error("Proxy unsupported in js client engine.")
    }

    /**
     * Create socks proxy from [host] and [port].
     */
    public actual fun socks(host: String, port: Int): ProxyConfig {
        error("Proxy unsupported in js client engine.")
    }
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.resolveAddress)
 */
public actual fun ProxyConfig.resolveAddress(): NetworkAddress {
    error("Proxy unsupported in js client engine.")
}

/**
 * Type of configured proxy.
 */
public actual val ProxyConfig.type: ProxyType
    get() = error("Proxy unsupported in js client engine.")
