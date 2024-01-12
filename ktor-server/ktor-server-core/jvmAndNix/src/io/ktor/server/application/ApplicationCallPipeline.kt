/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Pipeline configuration for executing [PipelineCall] instances.
 */
@Suppress("PublicApiImplicitType")
public open class ApplicationCallPipeline public constructor(
    final override val developmentMode: Boolean = false,
    public val environment: ApplicationEnvironment
) : Pipeline<Unit, PipelineCall>(
    Setup,
    Monitoring,
    Plugins,
    Call,
    Fallback
) {
    /**
     * Pipeline for receiving content
     */
    public val receivePipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(developmentMode)

    /**
     * Pipeline for sending content
     */
    public val sendPipeline: ApplicationSendPipeline = ApplicationSendPipeline(developmentMode)

    /**
     * Standard phases for application call pipelines
     */
    public companion object ApplicationPhase {
        /**
         * Phase for preparing call and it's attributes for processing
         */
        public val Setup: PipelinePhase = PipelinePhase("Setup")

        /**
         * Phase for tracing calls, useful for logging, metrics, error handling and so on
         */
        public val Monitoring: PipelinePhase = PipelinePhase("Monitoring")

        /**
         * Phase for plugins. Most plugins should intercept this phase.
         */
        public val Plugins: PipelinePhase = PipelinePhase("Plugins")

        /**
         * Phase for processing a call and sending a response
         */
        public val Call: PipelinePhase = PipelinePhase("Call")

        /**
         * Phase for handling unprocessed calls
         */
        public val Fallback: PipelinePhase = PipelinePhase("Fallback")

        /**
         * Phase for plugins. Most plugins should intercept this phase.
         */
        @Deprecated(
            "Renamed to Plugins",
            replaceWith = ReplaceWith("Plugins"),
            level = DeprecationLevel.ERROR
        )
        public val Features: PipelinePhase = Plugins
    }
}

/**
 * Current call for the context
 */
public inline val PipelineContext<*, PipelineCall>.call: PipelineCall get() = context

/**
 * Current application for the context
 */
public val PipelineContext<*, PipelineCall>.application: Application get() = call.application
