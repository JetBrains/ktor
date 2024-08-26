/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

/**
 * [TLSConfig] builder.
 */
public expect class TLSConfigBuilder() {
    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    public var serverName: String?

    /**
     * Create [TLSConfig].
     */
    public fun build(): TLSConfig
}

/**
 * Append config from [other] builder.
 */
public expect fun TLSConfigBuilder.takeFrom(other: TLSConfigBuilder)
