// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal actual class ConnectionPipeline actual constructor(
    keepAliveTime: Long,
    pipelineMaxSize: Int,
    connection: Connection,
    overProxy: Boolean,
    tasks: Channel<RequestTask>,
    parentContext: CoroutineContext
) : CoroutineScope {
    actual override val coroutineContext: CoroutineContext = parentContext

    init {
        error("Pipelining is not supported in native CIO")
    }

    actual val pipelineContext: Job
        get() = error("Pipelining is not supported in native CIO")
}
