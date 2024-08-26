/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.events.EventDefinition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

@InternalAPI
public val RoutingFailureStatusCode: AttributeKey<HttpStatusCode> = AttributeKey("RoutingFailureStatusCode")

internal val LOGGER = KtorSimpleLogger("io.ktor.server.routing.Routing")

/**
 * A root routing node of an [Application].
 * You can learn more about routing in Ktor from [Routing](https://ktor.io/docs/routing-in-ktor.html).
 *
 * @param application is an instance of [Application] for this routing node.
 */
@KtorDsl
public class RoutingRoot(
    public val application: Application
) : RoutingNode(
    parent = null,
    selector = RootRouteSelector(application.rootPath),
    application.developmentMode,
    application.environment
),
    Routing {
    private val tracers = mutableListOf<(RoutingResolveTrace) -> Unit>()

    init {
        addDefaultTracing()
    }

    private fun addDefaultTracing() {
        tracers.add {
            if (LOGGER.isTraceEnabled) {
                LOGGER.trace(it.buildText())
            }
        }
    }

    /**
     * Registers a function used to trace route resolution.
     * Might be useful if you need to understand why a route isn't executed.
     * To learn more, see [Tracing routes](https://ktor.io/docs/tracing-routes.html).
     */
    public override fun trace(block: (RoutingResolveTrace) -> Unit) {
        tracers.add(block)
    }

    @OptIn(InternalAPI::class)
    public suspend fun interceptor(context: PipelineContext<Unit, PipelineCall>) {
        val resolveContext = RoutingResolveContext(this, context.call, tracers)
        when (val resolveResult = resolveContext.resolve()) {
            is RoutingResolveResult.Success ->
                executeResult(context, resolveResult.route, resolveResult.parameters)

            is RoutingResolveResult.Failure ->
                context.call.attributes.put(RoutingFailureStatusCode, resolveResult.errorStatusCode)
        }
    }

    private suspend fun executeResult(
        context: PipelineContext<Unit, PipelineCall>,
        route: RoutingNode,
        parameters: Parameters
    ) {
        val routingCallPipeline = route.buildPipeline()
        val receivePipeline = merge(
            context.call.request.pipeline,
            routingCallPipeline.receivePipeline
        ) { ApplicationReceivePipeline(developmentMode) }

        val responsePipeline = merge(
            context.call.response.pipeline,
            routingCallPipeline.sendPipeline
        ) { ApplicationSendPipeline(developmentMode) }

        val routingApplicationCall = RoutingPipelineCall(
            context.call,
            route,
            context.coroutineContext,
            receivePipeline,
            responsePipeline,
            parameters
        )
        val routingCall = RoutingCall(routingApplicationCall)
        application.monitor.raise(RoutingCallStarted, routingCall)
        try {
            routingCallPipeline.execute(routingApplicationCall)
        } finally {
            application.monitor.raise(RoutingCallFinished, routingCall)
        }
    }

    private inline fun <Subject : Any, Context : Any, P : Pipeline<Subject, Context>> merge(
        first: P,
        second: P,
        build: () -> P
    ): P {
        if (first.isEmpty) {
            return second
        }
        if (second.isEmpty) {
            return first
        }
        return build().apply {
            merge(first)
            merge(second)
        }
    }

    /**
     * An installation object of the [RoutingRoot] plugin.
     */
    @Suppress("PublicApiImplicitType")
    public companion object Plugin : BaseApplicationPlugin<Application, Routing, RoutingRoot> {

        /**
         * A definition for an event that is fired when routing-based call processing starts.
         */
        public val RoutingCallStarted: EventDefinition<RoutingCall> = EventDefinition()

        /**
         * A definition for an event that is fired when routing-based call processing is finished.
         */
        public val RoutingCallFinished: EventDefinition<RoutingCall> = EventDefinition()

        override val key: AttributeKey<RoutingRoot> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit): RoutingRoot {
            val routingRoot = RoutingRoot(pipeline).apply(configure)
            pipeline.intercept(Call) { routingRoot.interceptor(this) }
            return routingRoot
        }
    }
}

/**
 * Gets an [Application] for this [RoutingNode] by scanning the hierarchy to the root.
 */
public val Route.application: Application
    get() = when (this) {
        is RoutingRoot -> application
        else -> parent?.application ?: throw UnsupportedOperationException(
            "Cannot retrieve application from unattached routing entry"
        )
    }

/**
 * Installs a [RoutingRoot] plugin for the this [Application] and runs a [configuration] script on it.
 * You can learn more about routing in Ktor from [Routing](https://ktor.io/docs/routing-in-ktor.html).
 */
@KtorDsl
public fun Application.routing(configuration: Routing.() -> Unit): RoutingRoot =
    pluginOrNull(RoutingRoot)?.apply(configuration) ?: install(RoutingRoot, configuration)
