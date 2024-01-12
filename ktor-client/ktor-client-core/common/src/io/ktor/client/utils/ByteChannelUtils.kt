/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.client.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(DelicateCoroutinesApi::class)
internal fun ByteReadChannel.observable(
    context: CoroutineContext,
    contentLength: Long?,
    listener: ProgressListener
) = GlobalScope.writer(context, autoFlush = true) {
    ByteArrayPool.useInstance { byteArray ->
        var bytesSend = 0L
        while (!this@observable.isClosedForRead) {
            val read = this@observable.readAvailable(byteArray)
            channel.writeFully(byteArray, offset = 0, length = read)
            bytesSend += read
            listener.onProgress(bytesSend, contentLength)
        }
        val closedCause = this@observable.closedCause
        channel.close(closedCause)
        if (closedCause == null && bytesSend == 0L) {
            listener.onProgress(bytesSend, contentLength)
        }
    }
}.channel
