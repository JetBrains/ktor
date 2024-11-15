/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * A config for the [SSE] plugin.
 */
public class SSEConfig {
    internal var showCommentEvents = false
    internal var showRetryEvents = false

    /**
     * The reconnection time. If the connection to the server is lost,
     * the client will wait for the specified time before attempting to reconnect.
     *
     * Note: this parameter is not supported for some engines.
     */
    public var reconnectionTime: Duration = 3000.milliseconds

    /**
     * Adds events consisting only of comments in the incoming flow.
     */
    public fun showCommentEvents() {
        showCommentEvents = true
    }

    /**
     * Adds events consisting only of the retry field in the incoming flow.
     */
    public fun showRetryEvents() {
        showRetryEvents = true
    }
}
