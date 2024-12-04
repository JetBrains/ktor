/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.*
import kotlin.coroutines.*
import io.ktor.utils.io.jvm.javaio.toByteReadChannel as toByteReadChannelImpl

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.cio.toByteReadChannel)
 */
@Deprecated(
    "Use variant from 'ktor-io' module instead",
    replaceWith = ReplaceWith(
        "this.toByteReadChannel(context + parent, pool)",
        "io.ktor.utils.io.jvm.javaio.toByteReadChannel",
    )
)
public fun InputStream.toByteReadChannel(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    context: CoroutineContext = Dispatchers.Unconfined,
    parent: Job = Job()
): ByteReadChannel = toByteReadChannelImpl(context + parent, pool)
