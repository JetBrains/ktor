/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import kotlinx.coroutines.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.nio.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

private val EmptyByteBuffer = ByteBuffer.allocate(0)!!

internal class JettyHttp2Request(private val stream: Stream) : Callback {
    private val deferred = AtomicReference<CancellableContinuation<Unit>?>()

    suspend fun write(src: ByteBuffer) = suspendCancellableCoroutine<Unit> { continuation ->
        deferred.set(continuation)

        val frame = DataFrame(stream.id, src, false)
        stream.data(frame, this)
    }

    override fun succeeded() {
        deferred.getAndSet(null)!!.resume(Unit)
    }

    override fun failed(cause: Throwable) {
        deferred.getAndSet(null)!!.resumeWithException(cause)
    }

    fun endBody() {
        stream.data(DataFrame(stream.id, EmptyByteBuffer, true), Callback.NOOP)
    }
}
