/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugin.tracing

import io.ktor.client.request.*
import io.ktor.websocket.*

/**
 * Tracer interface invoked at crucial points of the request processing to handle important events such as a start of
 * the request processing, a receiving of response headers, a receiving of data and so on. Implementations of this
 * interface are responsible for saving and presenting these events.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugin.tracing.Tracer)
 */
interface Tracer {
    /**
     * Indicates that the request processing has been start and request will be sent soon.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugin.tracing.Tracer.requestWillBeSent)
     */
    fun requestWillBeSent(requestId: String, requestData: HttpRequestData)

    /**
     * Indicates that the response processing has been started and headers were read.
     */
    fun responseHeadersReceived(requestId: String, requestData: HttpRequestData, responseData: HttpResponseData)

    /**
     * Wraps input channel to pass it to underlying implementation.
     */
    fun interpretResponse(requestId: String, contentType: String?, contentEncoding: String?, body: Any?): Any?

    /**
     * Indicates that communication with the server has failed.
     */
    fun httpExchangeFailed(requestId: String, message: String)

    /**
     * Indicates that communication with the server has finished.
     */
    fun responseReadFinished(requestId: String)

    /**
     * Invoked when a socket is created.
     */
    fun webSocketCreated(requestId: String, url: String)

    /**
     * Invoked specifically for websockets to communicate the WebSocket upgrade messages.
     */
    fun webSocketWillSendHandshakeRequest(requestId: String, requestData: HttpRequestData)

    /**
     * Delivers the reply from the peer in response to the WebSocket upgrade request.
     */
    fun webSocketHandshakeResponseReceived(
        requestId: String,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    )

    /**
     * Send a "websocket" frame from our app to the remote peer.
     */
    fun webSocketFrameSent(requestId: String, frame: Frame)

    /**
     * The receive side of {@link #webSocketFrameSent}.
     */
    fun webSocketFrameReceived(requestId: String, frame: Frame)

    /**
     * Socket has been closed for unknown reasons.
     */
    fun webSocketClosed(requestId: String)
}
