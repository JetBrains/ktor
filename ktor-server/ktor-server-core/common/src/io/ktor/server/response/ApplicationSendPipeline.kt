/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

/**
 * Server response send pipeline.
 */
public open class ApplicationSendPipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Any, PipelineCall>(Before, Transform, Render, ContentEncoding, TransferEncoding, After, Engine) {
    /**
     * Send pipeline phases.
     */
    @Suppress("PublicApiImplicitType")
    public companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * A transformation phase that can proceed with any supported data like String.
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * A phase to render any current pipeline subject into [io.ktor.http.content.OutgoingContent].
         *
         * Beyond this phase, only [io.ktor.http.content.OutgoingContent] should be produced by any interceptor.
         */
        public val Render: PipelinePhase = PipelinePhase("Render")

        /**
         * A phase for processing `Content-Encoding`, like compression and partial content.
         */
        public val ContentEncoding: PipelinePhase = PipelinePhase("ContentEncoding")

        /**
         * A phase for handling `Transfer-Encoding`, like if chunked encoding is being done manually and not by engine.
         */
        public val TransferEncoding: PipelinePhase = PipelinePhase("TransferEncoding")

        /**
         * The latest application phase that happens right before an engine sends a response.
         */
        public val After: PipelinePhase = PipelinePhase("After")

        /**
         * A phase for Engine to send the response out to the client.
         */
        public val Engine: PipelinePhase = PipelinePhase("Engine")
    }
}
