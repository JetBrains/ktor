/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Represents URL protocol
 * @property name of protocol (schema)
 * @property defaultPort default port for protocol or `-1` if not known
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.URLProtocol)
 */
public data class URLProtocol(val name: String, val defaultPort: Int) {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    @Suppress("PublicApiImplicitType")
    public companion object {
        /**
         * HTTP with port 80
         */
        public val HTTP: URLProtocol = URLProtocol("http", 80)

        /**
         * secure HTTPS with port 443
         */
        public val HTTPS: URLProtocol = URLProtocol("https", 443)

        /**
         * Web socket over HTTP on port 80
         */
        public val WS: URLProtocol = URLProtocol("ws", 80)

        /**
         * Web socket over secure HTTPS on port 443
         */
        public val WSS: URLProtocol = URLProtocol("wss", 443)

        /**
         * Socks proxy url protocol.
         */
        public val SOCKS: URLProtocol = URLProtocol("socks", 1080)

        /**
         * Protocols by names map
         */
        public val byName: Map<String, URLProtocol> = listOf(HTTP, HTTPS, WS, WSS, SOCKS).associateBy { it.name }

        /**
         * Create an instance by [name] or use already existing instance
         */
        public fun createOrDefault(name: String): URLProtocol = name.toLowerCasePreservingASCIIRules().let {
            byName[it] ?: URLProtocol(it, DEFAULT_PORT)
        }
    }
}

/**
 * Check if the protocol is websocket
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.isWebsocket)
 */
public fun URLProtocol.isWebsocket(): Boolean = name == "ws" || name == "wss"

/**
 * Check if the protocol is secure
 */
public fun URLProtocol.isSecure(): Boolean = name == "https" || name == "wss"
