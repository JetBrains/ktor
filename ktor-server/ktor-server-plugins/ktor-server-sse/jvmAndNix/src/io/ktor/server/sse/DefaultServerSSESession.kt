/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.sse.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.*
import kotlin.coroutines.*

internal class DefaultServerSSESession(
    private val output: ByteWriteChannel,
    override val call: ApplicationCall,
    override val coroutineContext: CoroutineContext
) : ServerSSESession {
    private val mutex = Mutex()

    override suspend fun send(event: ServerSentEvent) {
        mutex.withLock {
            output.writeSSE(event)
        }
    }

    override suspend fun close() {
        mutex.withLock {
            output.close()
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun ByteWriteChannel.writeSSE(event: ServerSentEvent) {
        writeStringUtf8(event.toString() + END_OF_LINE)
        flush()
    }
}
