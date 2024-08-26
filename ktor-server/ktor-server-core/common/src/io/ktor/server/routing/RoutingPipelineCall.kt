/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * An application call handled by [RoutingRoot].
 * @property call original call from [io.ktor.server.engine.ApplicationEngine]
 * @property route is the selected route
 */
public class RoutingPipelineCall(
    public val engineCall: PipelineCall,
    public val route: RoutingNode,
    override val coroutineContext: CoroutineContext,
    receivePipeline: ApplicationReceivePipeline,
    responsePipeline: ApplicationSendPipeline,
    public val pathParameters: Parameters
) : PipelineCall, CoroutineScope {

    override val application: Application get() = engineCall.application
    override val attributes: Attributes get() = engineCall.attributes

    override val request: RoutingPipelineRequest =
        RoutingPipelineRequest(this, receivePipeline, engineCall.request)

    override val response: RoutingPipelineResponse =
        RoutingPipelineResponse(this, responsePipeline, engineCall.response)

    override val parameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        Parameters.build {
            appendAll(engineCall.parameters)
            appendMissing(pathParameters)
        }
    }

    override fun toString(): String = "RoutingApplicationCall(route=$route)"
}

/**
 * An application request handled by [RoutingRoot].
 */
public class RoutingPipelineRequest(
    override val call: RoutingPipelineCall,
    override val pipeline: ApplicationReceivePipeline,
    public val engineRequest: PipelineRequest
) : PipelineRequest by engineRequest

/**
 * An application response handled by [RoutingRoot].
 */
public class RoutingPipelineResponse(
    override val call: RoutingPipelineCall,
    override val pipeline: ApplicationSendPipeline,
    public val engineResponse: PipelineResponse
) : PipelineResponse by engineResponse
