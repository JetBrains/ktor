/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.server.application.*

/**
 * A server's response.
 * To learn how to send responses inside route handlers, see [Sending responses](https://ktor.io/docs/responses.html).
 * @see [ApplicationCall]
 * @see [io.ktor.server.request.ApplicationRequest]
 */
public interface ApplicationResponse {
    /**
     * Provides access to headers for the current response.
     */
    public val headers: ResponseHeaders

    /**
     * An [ApplicationCall] instance this [ApplicationResponse] is attached to.
     */
    public val call: ApplicationCall

    /**
     * Indicates that this response is already committed and no further changes are allowed.
     */
    public val isCommitted: Boolean

    /**
     * Indicates that this response is already fully sent to the client.
     */
    public val isSent: Boolean

    /**
     * Provides access to cookies for this response.
     */
    public val cookies: ResponseCookies

    /**
     * Returns a response status code or `null` if a status code is not set.
     */
    public fun status(): HttpStatusCode?

    /**
     * Specifies a status code for a response.
     */
    public fun status(value: HttpStatusCode)

    /**
     * Produces HTTP/2 push from a server to a client or sets an HTTP/1.x hint header
     * or does nothing. Exact behaviour is up to engine implementation.
     */
    @UseHttp2Push
    public fun push(builder: ResponsePushBuilder)
}

/**
 * A server's response that is used in [ApplicationPlugin].
 * To learn how to send responses inside route handlers, see [Sending responses](https://ktor.io/docs/responses.html).
 * @see [PipelineCall]
 * @see [io.ktor.server.request.PipelineRequest]
 */
public interface PipelineResponse : ApplicationResponse {
    /**
     * An [PipelineCall] instance this [PipelineResponse] is attached to.
     */
    public override val call: PipelineCall

    /**
     * A pipeline for sending content.
     */
    public val pipeline: ApplicationSendPipeline
}
