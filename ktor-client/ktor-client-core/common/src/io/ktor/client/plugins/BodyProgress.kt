/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.content.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

private val UploadProgressListenerAttributeKey =
    AttributeKey<ProgressListener>("UploadProgressListenerAttributeKey")

private val DownloadProgressListenerAttributeKey =
    AttributeKey<ProgressListener>("DownloadProgressListenerAttributeKey")

/**
 * Plugin that provides observable progress for uploads and downloads
 */
public val BodyProgress: ClientPlugin<Unit> = createClientPlugin("BodyProgress") {

    on(AfterRenderHook) { request, content ->
        val listener = request.attributes
            .getOrNull(UploadProgressListenerAttributeKey) ?: return@on null

        ObservableContent(content, request.executionContext, listener)
    }

    on(AfterReceiveHook) { response ->
        val listener = response.call.request.attributes
            .getOrNull(DownloadProgressListenerAttributeKey) ?: return@on null
        response.withObservableDownload(listener)
    }
}

internal object AfterReceiveHook : ClientHook<suspend (HttpResponse) -> HttpResponse?> {
    override fun install(client: HttpClient, handler: suspend (HttpResponse) -> HttpResponse?) {
        client.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
            val newResponse = handler(response)
            if (newResponse != null) proceedWith(newResponse)
        }
    }
}

internal object AfterRenderHook : ClientHook<suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?> {
    override fun install(
        client: HttpClient,
        handler: suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?
    ) {
        val observableContentPhase = PipelinePhase("ObservableContent")
        client.requestPipeline.insertPhaseAfter(reference = HttpRequestPipeline.Render, phase = observableContentPhase)
        client.requestPipeline.intercept(observableContentPhase) { content ->
            if (content !is OutgoingContent) return@intercept
            val newContent = handler(context, content) ?: return@intercept
            proceedWith(newContent)
        }
    }
}

@OptIn(InternalAPI::class)
internal fun HttpResponse.withObservableDownload(listener: ProgressListener): HttpResponse {
    val observableByteChannel = content.observable(coroutineContext, contentLength(), listener)
    return call.wrapWithContent(observableByteChannel).response
}

/**
 * Registers listener to observe download progress.
 */
public fun HttpRequestBuilder.onDownload(listener: ProgressListener?) {
    if (listener == null) {
        attributes.remove(DownloadProgressListenerAttributeKey)
    } else {
        attributes.put(DownloadProgressListenerAttributeKey, listener)
    }
}

/**
 * Registers listener to observe upload progress.
 */
public fun HttpRequestBuilder.onUpload(listener: ProgressListener?) {
    if (listener == null) {
        attributes.remove(UploadProgressListenerAttributeKey)
    } else {
        attributes.put(UploadProgressListenerAttributeKey, listener)
    }
}
