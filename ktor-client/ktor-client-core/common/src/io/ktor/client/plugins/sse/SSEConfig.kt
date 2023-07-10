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
    internal val closeConditions: MutableList<(String) -> Boolean> = mutableListOf()
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
     * Allows you to configure a condition for message on which SSESession wil be closed.
     */
    public fun closeOn(condition: (String) -> Boolean) {
        closeConditions.add(condition)
    }

    /**
     * Add events consisting only of comments in incoming flow.
     */
    public fun showCommentEvents() {
        showCommentEvents = true
    }

    /**
     * Add events consisting only of retry field in incoming flow.
     */
    public fun showRetryEvents() {
        showRetryEvents = true
    }
}
