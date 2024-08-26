/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

internal object BodyTransformedHook :
    Hook<suspend BodyTransformedHook.Context.(call: ApplicationCall, message: Any) -> Unit> {
    class Context(private val context: PipelineContext<Any, PipelineCall>) {
        val call: ApplicationCall = context.call

        fun transformBodyTo(newValue: Any) {
            context.subject = newValue
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(call: ApplicationCall, message: Any) -> Unit
    ) {
        val partialContentPhase = PipelinePhase("PartialContent")
        pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.ContentEncoding, partialContentPhase)
        pipeline.sendPipeline.intercept(partialContentPhase) { message ->
            Context(this).handler(call, message)
        }
    }
}

internal suspend fun BodyTransformedHook.Context.tryProcessRange(
    content: OutgoingContent.ReadChannelContent,
    call: ApplicationCall,
    rangesSpecifier: RangesSpecifier,
    length: Long,
    maxRangeCount: Int
) {
    if (checkIfRangeHeader(content, call)) {
        processRange(content, rangesSpecifier, length, maxRangeCount)
    } else {
        transformBodyTo(PartialOutgoingContent.Bypass(content))
    }
}
