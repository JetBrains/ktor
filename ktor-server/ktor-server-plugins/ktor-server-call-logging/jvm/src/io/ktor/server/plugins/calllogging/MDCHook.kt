/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

internal fun MDCHook(phase: PipelinePhase) = object : Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit
    ) {
        val mdcPhase = PipelinePhase("${phase.name}MDC")
        pipeline.insertPhaseBefore(phase, mdcPhase)

        pipeline.intercept(mdcPhase) {
            handler(call, ::proceed)
        }
    }
}

internal object ResponseSent : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Engine) {
            if (call.attributes.contains(responseSentMarker)) return@intercept

            call.attributes.put(responseSentMarker, Unit)
            proceed()
            handler(call)
        }
    }
}

private val responseSentMarker = AttributeKey<Unit>("ResponseSentTriggered")
