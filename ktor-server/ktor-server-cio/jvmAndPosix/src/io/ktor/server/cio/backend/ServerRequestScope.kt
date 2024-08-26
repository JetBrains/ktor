/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents a request scope.
 * @property upgraded deferred should be completed on upgrade request
 * @property input channel connected to request body bytes stream
 * @property output channel connected to response body
 * @property remoteAddress of the client (if known)
 * @property localAddress on which the client was accepted (if known)
 */
public class ServerRequestScope internal constructor(
    override val coroutineContext: CoroutineContext,
    public val input: ByteReadChannel,
    public val output: ByteWriteChannel,
    public val remoteAddress: NetworkAddress?,
    public val localAddress: NetworkAddress?,
    public val upgraded: CompletableDeferred<Boolean>?
) : CoroutineScope {
    /**
     * Creates another request scope with same parameters except coroutine context
     */
    public fun withContext(coroutineContext: CoroutineContext): ServerRequestScope =
        ServerRequestScope(
            this.coroutineContext + coroutineContext,
            input,
            output,
            remoteAddress,
            localAddress,
            upgraded
        )
}
