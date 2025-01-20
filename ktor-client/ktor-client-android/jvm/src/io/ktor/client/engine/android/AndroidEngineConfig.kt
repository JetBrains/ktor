/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.net.http.*
import io.ktor.client.engine.*
import java.net.*
import javax.net.ssl.*

/**
 * A configuration for the [Android] client engine.
 */
public class AndroidEngineConfig : HttpClientEngineConfig() {
    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     */
    public var connectTimeout: Int = 100_000

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     */
    public var socketTimeout: Int = 100_000

    /**
     * Allows you to configure [HTTPS](https://ktor.io/docs/client-ssl.html) settings for this engine.
     */
    public var sslManager: (HttpsURLConnection) -> Unit = {}

    /**
     * Allows you to set engine-specific request configuration.
     */
    public var requestConfig: HttpURLConnection.() -> Unit = {}

    /**
     * Allows you to set engine-specific request configuration.
     */
    public var httpEngineConfig: HttpEngine.Builder.() -> Unit = {}

    internal var httpEngineDisabled = false

    /**
     * Allows you to set engine-specific request configuration.
     */
    public var context: android.content.Context? = null
        set(value) {
            field = value
        }
}
