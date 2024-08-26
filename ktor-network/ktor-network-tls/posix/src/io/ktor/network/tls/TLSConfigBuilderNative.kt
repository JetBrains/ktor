/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

/**
 * [TLSConfig] builder.
 */
public actual class TLSConfigBuilder {
    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    public actual var serverName: String? = null

    /**
     * Create [TLSConfig].
     */
    public actual fun build(): TLSConfig = TLSConfig()
}

/**
 * Append config from [other] builder.
 */
public actual fun TLSConfigBuilder.takeFrom(other: TLSConfigBuilder) {
}
