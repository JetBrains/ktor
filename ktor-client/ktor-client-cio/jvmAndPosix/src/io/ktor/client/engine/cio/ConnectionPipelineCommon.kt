// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal expect class ConnectionPipeline(
    keepAliveTime: Long,
    pipelineMaxSize: Int,
    connection: Connection,
    overProxy: Boolean,
    tasks: Channel<RequestTask>,
    parentContext: CoroutineContext
) : CoroutineScope {
    val pipelineContext: Job

    override val coroutineContext: CoroutineContext
}
