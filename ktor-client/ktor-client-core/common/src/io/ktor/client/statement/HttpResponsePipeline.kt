/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*

/**
 * [HttpClient] Pipeline used for executing [HttpResponse].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpResponsePipeline)
 */
public class HttpResponsePipeline(
    override val developmentMode: Boolean = true
) : Pipeline<HttpResponseContainer, HttpClientCall>(
    Receive,
    Parse,
    Transform,
    State,
    After
) {
    public companion object Phases {
        /**
         * The earliest phase that happens before any other
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpResponsePipeline.Phases.Receive)
         */
        public val Receive: PipelinePhase = PipelinePhase("Receive")

        /**
         * Decode response body
         */
        public val Parse: PipelinePhase = PipelinePhase("Parse")

        /**
         * Transform response body to expected format
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * Use this phase to store request shared state
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Latest response pipeline phase
         */
        public val After: PipelinePhase = PipelinePhase("After")
    }
}

/**
 * [HttpClient] Pipeline used for receiving [HttpResponse] without any processing.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpReceivePipeline)
 */
public class HttpReceivePipeline(
    override val developmentMode: Boolean = true
) : Pipeline<HttpResponse, Unit>(Before, State, After) {
    public companion object Phases {
        /**
         * The earliest phase that happens before any other
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpReceivePipeline.Phases.Before)
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Use this phase to store request shared state
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Latest response pipeline phase
         */
        public val After: PipelinePhase = PipelinePhase("After")
    }
}

/**
 * Class representing a typed [response] with an attached [expectedType].
 * @param expectedType: information about expected type.
 * @param response: current response state.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpResponseContainer)
 */
public data class HttpResponseContainer(val expectedType: TypeInfo, val response: Any)
