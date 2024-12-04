/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Application engine pipeline. One usually don't need to install interceptors here unless your are writing
 * your own engine implementation
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.engine.EnginePipeline)
 */
public class EnginePipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Unit, PipelineCall>(Before, Call) {
    /**
     * Pipeline for receiving content
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.engine.EnginePipeline.receivePipeline)
     */
    public val receivePipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(developmentMode)

    /**
     * Pipeline for sending content
     */
    public val sendPipeline: ApplicationSendPipeline = ApplicationSendPipeline(developmentMode)

    public companion object {
        /**
         * Before call phase
         */
        public val Before: PipelinePhase = PipelinePhase("before")

        /**
         * Application call pipeline phase
         */
        public val Call: PipelinePhase = PipelinePhase("call")
    }
}
