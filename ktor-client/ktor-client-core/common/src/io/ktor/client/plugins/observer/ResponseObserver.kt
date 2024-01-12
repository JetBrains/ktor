/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * [ResponseObserver] callback.
 */
public typealias ResponseHandler = suspend (HttpResponse) -> Unit

@KtorDsl
public class ResponseObserverConfig {
    internal var responseHandler: ResponseHandler = {}

    internal var filter: ((HttpClientCall) -> Boolean)? = null

    /**
     * Set response handler for logging.
     */
    public fun onResponse(block: ResponseHandler) {
        responseHandler = block
    }

    /**
     * Set filter predicate to dynamically control if interceptor is executed or not for each http call.
     */
    public fun filter(block: ((HttpClientCall) -> Boolean)) {
        filter = block
    }
}

/**
 * Observe response plugin.
 */
@OptIn(InternalAPI::class)
public val ResponseObserver: ClientPlugin<ResponseObserverConfig> = createClientPlugin(
    "ResponseObserver",
    ::ResponseObserverConfig
) {

    val responseHandler: ResponseHandler = pluginConfig.responseHandler
    val filter: ((HttpClientCall) -> Boolean)? = pluginConfig.filter

    on(AfterReceiveHook) { response ->
        if (filter?.invoke(response.call) == false) return@on

        val (loggingContent, responseContent) = response.content.split(response)

        val newResponse = response.call.wrapWithContent(responseContent).response
        val sideResponse = response.call.wrapWithContent(loggingContent).response

        client.launch(getResponseObserverContext()) {
            runCatching { responseHandler(sideResponse) }

            val content = sideResponse.content
            if (!content.isClosedForRead) {
                runCatching { content.discard() }
            }
        }

        proceedWith(newResponse)
    }
}

private object AfterReceiveHook : ClientHook<suspend AfterReceiveHook.Context.(HttpResponse) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponse, Unit>) {
        suspend fun proceedWith(response: HttpResponse) = context.proceedWith(response)
    }

    override fun install(client: HttpClient, handler: suspend Context.(HttpResponse) -> Unit) {
        client.receivePipeline.intercept(HttpReceivePipeline.After) {
            handler(Context(this), subject)
        }
    }
}

internal expect suspend fun getResponseObserverContext(): CoroutineContext

/**
 * Install [ResponseObserver] plugin in client.
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.ResponseObserver(block: ResponseHandler) {
    install(ResponseObserver) {
        responseHandler = block
    }
}
