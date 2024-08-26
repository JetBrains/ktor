/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.statuspages

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.logging.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.jvm.*
import kotlin.reflect.*

private val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.statuspages.StatusPages")

/**
 * Specifies how the exception should be handled.
 */
public typealias HandlerFunction = suspend (call: ApplicationCall, cause: Throwable) -> Unit

/**
 * A plugin that handles exceptions and status codes. Useful to configure default error pages.
 */
public val StatusPages: ApplicationPlugin<StatusPagesConfig> = createApplicationPlugin(
    "StatusPages",
    ::StatusPagesConfig
) {
    val statusPageMarker = AttributeKey<Unit>("StatusPagesTriggered")

    val exceptions = HashMap(pluginConfig.exceptions)
    val statuses = HashMap(pluginConfig.statuses)
    val unhandled = pluginConfig.unhandled

    fun findHandlerByValue(cause: Throwable): HandlerFunction? {
        val keys = exceptions.keys.filter { cause.instanceOf(it) }
        if (keys.isEmpty()) return null

        if (keys.size == 1) {
            return exceptions[keys.single()]
        }

        val key = selectNearestParentClass(cause, keys)
        return exceptions[key]
    }

    on(ResponseBodyReadyForSend) { call, content ->
        if (call.attributes.contains(statusPageMarker)) return@on

        val status = content.status ?: call.response.status()
        if (status == null) {
            LOGGER.trace("No status code found for call: ${call.request.uri}")
            return@on
        }

        val handler = statuses[status]
        if (handler == null) {
            LOGGER.trace("No handler found for status code $status for call: ${call.request.uri}")
            return@on
        }

        call.attributes.put(statusPageMarker, Unit)
        try {
            LOGGER.trace("Executing $handler for status code $status for call: ${call.request.uri}")
            handler(call, content, status)
        } catch (cause: Throwable) {
            LOGGER.trace(
                "Exception $cause while executing $handler for status code $status for call: ${call.request.uri}"
            )
            call.attributes.remove(statusPageMarker)
            throw cause
        }
    }

    on(CallFailed) { call, cause ->
        if (call.attributes.contains(statusPageMarker)) return@on

        LOGGER.trace("Call ${call.request.uri} failed with cause $cause")

        val handler = findHandlerByValue(cause)
        if (handler == null) {
            LOGGER.trace("No handler found for exception: $cause for call ${call.request.uri}")
            throw cause
        }

        call.attributes.put(statusPageMarker, Unit)
        call.application.mdcProvider.withMDCBlock(call) {
            LOGGER.trace("Executing $handler for exception $cause for call ${call.request.uri}")
            handler(call, cause)
        }
    }

    on(BeforeFallback) { call ->
        if (call.isHandled) return@on
        unhandled(call)
    }
}

/**
 * A [StatusPages] plugin configuration.
 */
@KtorDsl
public class StatusPagesConfig {
    /**
     * Provides access to exception handlers of the exception class.
     */
    public val exceptions: MutableMap<KClass<*>, HandlerFunction> = mutableMapOf()

    /**
     * Provides access to status handlers based on a status code.
     */
    public val statuses: MutableMap<
        HttpStatusCode,
        suspend (call: ApplicationCall, content: OutgoingContent, code: HttpStatusCode) -> Unit
        > =
        mutableMapOf()

    internal var unhandled: suspend (ApplicationCall) -> Unit = {}

    /**
     * Register an exception [handler] for the exception type [T] and its children.
     */
    public inline fun <reified T : Throwable> exception(
        noinline handler: suspend (call: ApplicationCall, cause: T) -> Unit
    ): Unit = exception(T::class, handler)

    /**
     * Register an exception [handler] for the exception class [klass] and its children.
     */
    public fun <T : Throwable> exception(
        klass: KClass<T>,
        handler: suspend (call: ApplicationCall, T) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val cast = handler as suspend (ApplicationCall, Throwable) -> Unit

        exceptions[klass] = cast
    }

    /**
     * Register a status [handler] for the [status] code.
     */
    public fun status(
        vararg status: HttpStatusCode,
        handler: suspend (ApplicationCall, HttpStatusCode) -> Unit
    ) {
        status.forEach {
            statuses[it] = { call, _, code -> handler(call, code) }
        }
    }

    /**
     * Register a [handler] for the unhandled calls.
     */
    public fun unhandled(handler: suspend (ApplicationCall) -> Unit) {
        unhandled = handler
    }

    /**
     * Register a status [handler] for the [status] code.
     */
    @JvmName("statusWithContext")
    public fun status(
        vararg status: HttpStatusCode,
        handler: suspend StatusContext.(HttpStatusCode) -> Unit
    ) {
        status.forEach {
            statuses[it] = { call, content, code -> handler(StatusContext(call, content), code) }
        }
    }

    /**
     * A context for [status] config method.
     */
    public class StatusContext(
        public val call: ApplicationCall,
        public val content: OutgoingContent
    )
}
