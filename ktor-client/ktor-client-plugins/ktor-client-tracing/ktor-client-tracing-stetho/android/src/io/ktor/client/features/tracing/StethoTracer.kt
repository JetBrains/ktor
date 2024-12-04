/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugin.tracing

import android.content.*
import com.facebook.stetho.*
import com.facebook.stetho.inspector.network.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

/**
 * Shortcut that allows to easily wrap engine factory into Stetho tracer.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugin.tracing.Stetho)
 */
fun <T : HttpClientEngineConfig> Context.Stetho(delegate: HttpClientEngineFactory<T>): HttpClientEngineFactory<T> =
    TracingWrapper(delegate, StethoTracer(this))

/**
 * Implementation of [Tracer] based on Stetho Android library.
 */
class StethoTracer(context: Context) : Tracer {
    /**
     * Stetho event reporter used as a communication point with Stetho internals that sends data to Chrome Dev Tools.
     */
    private val reporter: NetworkEventReporter = NetworkEventReporterImpl.get()

    init {
        Stetho.initializeWithDefaults(context)
    }

    override fun requestWillBeSent(requestId: String, requestData: HttpRequestData) {
        reporter.requestWillBeSent(
            KtorInterceptorRequest(
                requestId,
                requestData
            )
        )
    }

    override fun responseHeadersReceived(
        requestId: String,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    ) {
        reporter.responseHeadersReceived(
            KtorInterceptorResponse(
                requestId,
                requestData,
                responseData
            )
        )
    }

    override fun interpretResponse(
        requestId: String,
        contentType: String?,
        contentEncoding: String?,
        body: Any?
    ): Any? {
        return reporter.interpretResponseStream(
            requestId,
            contentType,
            contentEncoding,
            (body as ByteChannel).toInputStream(),
            DefaultResponseHandler(reporter, requestId)
        )?.toByteReadChannel()
    }

    override fun httpExchangeFailed(requestId: String, message: String) {
        reporter.httpExchangeFailed(requestId, message)
    }

    override fun responseReadFinished(requestId: String) {
        reporter.responseReadFinished(requestId)
    }

    override fun webSocketCreated(requestId: String, url: String) {
        reporter.webSocketCreated(requestId, url)
    }

    override fun webSocketWillSendHandshakeRequest(requestId: String, requestData: HttpRequestData) {
        reporter.webSocketWillSendHandshakeRequest(KtorInspectorWebSocketRequest(requestId, requestData))
    }

    override fun webSocketHandshakeResponseReceived(
        requestId: String,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    ) {
        reporter.webSocketHandshakeResponseReceived(
            KtorInspectorWebSocketResponse(
                requestId,
                requestData,
                responseData
            )
        )
    }

    override fun webSocketFrameSent(requestId: String, frame: Frame) {
        reporter.webSocketFrameSent(KtorInspectorWebSocketFrame(requestId, frame))
    }

    override fun webSocketFrameReceived(requestId: String, frame: Frame) {
        reporter.webSocketFrameReceived(KtorInspectorWebSocketFrame(requestId, frame))
    }

    override fun webSocketClosed(requestId: String) {
        reporter.webSocketClosed(requestId)
    }
}
