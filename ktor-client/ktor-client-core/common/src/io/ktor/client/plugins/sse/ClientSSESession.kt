/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.call.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A Server-sent events session.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.sse.SSESession)
 */
public interface SSESession : CoroutineScope {
    /**
     * An incoming server-sent events flow.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.sse.SSESession.incoming)
     */
    public val incoming: Flow<ServerSentEvent>
}

/**
 * A client Server-sent events session.
 *
 * @property call associated with session.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.sse.ClientSSESession)
 */
public class ClientSSESession(public val call: HttpClientCall, delegate: SSESession) : SSESession by delegate
