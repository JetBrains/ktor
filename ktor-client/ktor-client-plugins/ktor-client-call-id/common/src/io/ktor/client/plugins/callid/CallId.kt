/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.callid

import io.ktor.callid.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.coroutines.*

internal typealias CallIdGenerator = suspend (HttpRequestBuilder) -> String?
internal typealias CallIdInterceptor = (request: HttpRequestBuilder, callId: String) -> Unit

/**
 * A configuration for [CallId] plugin.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.callid.CallIdConfig)
 */
public class CallIdConfig {

    internal val generators = mutableListOf<CallIdGenerator>()
    internal val requestInterceptors = mutableListOf<CallIdInterceptor>()

    /**
     * If set to `true`, adds a default generator that uses current [CoroutineContext] to retrieve a call ID.
     *
     * @see withCallId
     * @see KtorCallIdContextElement
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.callid.CallIdConfig.useCoroutineContext)
     */
    public var useCoroutineContext: Boolean = true

    /**
     * Allows you to generate a call ID for an outgoing request.
     * Generates `null` if it is impossible to generate a call ID for some reason.
     * You can add multiple generators, and the first non-null value will be used.
     */
    public fun generate(block: suspend (HttpRequestBuilder) -> String?) {
        generators.add(block)
    }

    /**
     * Allows you to add a call ID to the request.
     *
     * @see [addToHeader]
     */
    public fun intercept(block: CallIdInterceptor) {
        requestInterceptors.add(block)
    }

    /**
     * Adds a call ID to specified header named [header].
     *
     * @see [intercept]
     */
    public fun addToHeader(header: String = HttpHeaders.XRequestId) {
        intercept { request, callId -> request.header(header, callId) }
    }
}

/**
 * A plugin that allows you to trace client requests end-to-end by using unique request IDs or call IDs.
 * Typically, working with a call ID in the Ktor client might look as follows:
 * 1. First, you need to obtain a call ID for a specific request in one of the following ways:
 *    - A calling scope may already have a call ID in its coroutine context.
 *    - Otherwise, if a scope comes without a call ID, you can generate it.
 * 2. The plugin will add a call ID to the coroutine context for this call.
 * Usually, it's done in the specific header, such as [HttpHeaders.XRequestId].
 *
 * The default behavior of this plugin is to take a call ID from the coroutine context
 * and add it to the [HttpHeaders.XRequestId]. See [CallIdConfig] if you want to change it.
 *
 * You can learn more from [CallId](https://ktor.io/docs/call-id.html).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.callid.CallId)
 */
public val CallId: ClientPlugin<CallIdConfig> = createClientPlugin("CallId", ::CallIdConfig) {

    val generators = pluginConfig.generators.toMutableList()
    val interceptors = pluginConfig.requestInterceptors.toMutableList()

    if (pluginConfig.useCoroutineContext) {
        generators.add(0) { coroutineContext[KtorCallIdContextElement]?.callId }
    }
    if (interceptors.isEmpty()) {
        interceptors.add { request, callId -> request.header(HttpHeaders.XRequestId, callId) }
    }

    onRequest { request, _ ->
        val callId = generators.firstNotNullOfOrNull { it(request) } ?: return@onRequest
        interceptors.forEach { it(request, callId) }
    }
}
