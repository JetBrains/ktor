/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

/**
 * Base class for implementing [PipelineRequest]
 */
public abstract class BaseApplicationRequest(final override val call: PipelineCall) : PipelineRequest {

    protected abstract val engineHeaders: Headers
    protected abstract val engineReceiveChannel: ByteReadChannel
    private val receiveChannel: AtomicRef<ByteReadChannel?> = atomic(null)

    final override val headers: Headers by lazy { DelegateHeaders(engineHeaders) }

    override val pipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(
        call.application.developmentMode
    ).apply {
        resetFrom(call.application.receivePipeline)
    }

    final override fun receiveChannel(): ByteReadChannel {
        return receiveChannel.value ?: engineReceiveChannel
    }

    @InternalAPI
    final override fun setHeader(name: String, values: List<String>?) {
        (headers as DelegateHeaders).setHeader(name, values)
    }

    @InternalAPI
    final override fun setReceiveChannel(channel: ByteReadChannel) {
        receiveChannel.value = channel
    }
}

private class DelegateHeaders(private val original: Headers) : Headers {

    private val builder = HeadersBuilder().apply {
        appendAll(original)
    }

    private val headers = atomic(builder.build())

    override val caseInsensitiveName: Boolean = headers.value.caseInsensitiveName

    fun setHeader(name: String, values: List<String>?) {
        builder.remove(name)
        if (values != null) {
            builder.appendAll(name, values)
        }
        headers.value = builder.build()
    }

    override fun getAll(name: String): List<String>? {
        return headers.value.getAll(name)
    }

    override fun names(): Set<String> {
        return headers.value.names()
    }

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return headers.value.entries()
    }

    override fun isEmpty(): Boolean {
        return headers.value.isEmpty()
    }
}
