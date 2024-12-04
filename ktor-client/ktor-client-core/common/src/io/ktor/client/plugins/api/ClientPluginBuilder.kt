/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * An utility class used to build a [ClientPlugin] instance.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.ClientPluginBuilder)
 **/
@KtorDsl
public class ClientPluginBuilder<PluginConfig : Any> internal constructor(
    internal val key: AttributeKey<ClientPluginInstance<PluginConfig>>,
    /**
     * A reference to the [HttpClient] where the plugin is installed.
     **/
    public val client: HttpClient,
    /**
     * A configuration of the current plugin.
     */
    public val pluginConfig: PluginConfig
) {

    internal val hooks: MutableList<HookHandler<*>> = mutableListOf()
    internal var onClose: () -> Unit = {}

    /**
     * Specifies the [block] handler for every HTTP request.
     *
     * This block is invoked for every [HttpClient.request] call.
     * There you can modify the request in a way you want: add headers, configure logging, etc.
     *
     * @see [createClientPlugin]
     *
     * @param block An action that needs to be executed when a client creates an HTTP request.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.ClientPluginBuilder.onRequest)
     */
    public fun onRequest(
        block: suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit
    ) {
        on(RequestHook, block)
    }

    /**
     * Specifies the [block] handler for every HTTP response.
     *
     * This block is invoked for every incoming response.
     * There you can inspect the response in a way you want: save cookies, add logging, etc.
     *
     * @see [createClientPlugin]
     *
     * @param block An action that needs to be executed when a client receives an HTTP response.
     */
    public fun onResponse(
        block: suspend OnResponseContext.(response: HttpResponse) -> Unit
    ) {
        on(ResponseHook, block)
    }

    /**
     * Specifies the [block] transformer for a request body.
     *
     * This block is invoked for every [HttpClient.request] call.
     * Here you should serialize body into [OutgoingContent] or return `null` if your transformation is not applicable.
     *
     * @see [createClientPlugin]
     *
     * @param block A transformation of request body.
     */
    public fun transformRequestBody(
        block: suspend TransformRequestBodyContext.(
            request: HttpRequestBuilder,
            content: Any,
            bodyType: TypeInfo?
        ) -> OutgoingContent?
    ) {
        on(TransformRequestBodyHook, block)
    }

    /**
     * Specifies the [block] transformer for a response body.
     *
     * This block is invoked for every [HttpResponse.body] call.
     * Here you should deserialize body into an instance of [requestedType]
     * or return `null` if your transformation is not applicable.
     *
     * @see [createClientPlugin]
     *
     * @param block A transformation of response body.
     */
    public fun transformResponseBody(
        block: suspend TransformResponseBodyContext.(
            response: HttpResponse,
            content: ByteReadChannel,
            requestedType: TypeInfo
        ) -> Any?
    ) {
        on(TransformResponseBodyHook, block)
    }

    /**
     * Specifies the [block] to clean resources allocated with this plugin.
     */
    public fun onClose(block: () -> Unit) {
        onClose = block
    }

    /**
     * Specifies a [handler] for a specific [hook].
     * A [hook] can be a specific place in time or event during the request
     * processing like receiving a response, an exception during call processing, etc.
     *
     * @see [createClientPlugin]
     */
    public fun <HookHandler> on(
        hook: ClientHook<HookHandler>,
        handler: HookHandler
    ) {
        hooks.add(HookHandler(hook, handler))
    }
}
