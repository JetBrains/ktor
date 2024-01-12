/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * HttpSend pipeline interceptor function
 */
public typealias HttpSendInterceptor = suspend Sender.(HttpRequestBuilder) -> HttpClientCall

/**
 * This interface represents a request send pipeline interceptor chain
 */
public interface Sender {
    /**
     * Execute send pipeline. It could start pipeline execution or replace the call
     */
    public suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall
}

/**
 * This is an internal plugin that is always installed.
 */
public class HttpSend private constructor(
    private val maxSendCount: Int = 20
) {

    @KtorDsl
    public class Config {
        /**
         * Maximum number of requests that can be sent during a call
         */
        public var maxSendCount: Int = 20
    }

    private val interceptors: MutableList<HttpSendInterceptor> = mutableListOf()

    /**
     * Install send pipeline starter interceptor
     */
    public fun intercept(block: HttpSendInterceptor) {
        interceptors += block
    }

    /**
     * A plugin's installation object
     */
    public companion object Plugin : HttpClientPlugin<Config, HttpSend> {
        override val key: AttributeKey<HttpSend> = AttributeKey("HttpSend")

        override fun prepare(block: Config.() -> Unit): HttpSend {
            val config = Config().apply(block)
            return HttpSend(config.maxSendCount)
        }

        override fun install(plugin: HttpSend, scope: HttpClient) {
            // default send scenario
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { content ->
                check(content is OutgoingContent) {
                    """
|Fail to prepare request body for sending. 
|The body type is: ${content::class}, with Content-Type: ${context.contentType()}.
|
|If you expect serialized body, please check that you have installed the corresponding plugin(like `ContentNegotiation`) and set `Content-Type` header."""
                        .trimMargin()
                }
                context.setBody(content)

                val realSender: Sender = DefaultSender(plugin.maxSendCount, scope)
                var interceptedSender = realSender
                for (interceptor in plugin.interceptors.reversed()) {
                    interceptedSender = InterceptedSender(interceptor, interceptedSender)
                }
                val call = interceptedSender.execute(context)
                proceedWith(call)
            }
        }
    }

    private class InterceptedSender(
        private val interceptor: HttpSendInterceptor,
        private val nextSender: Sender
    ) : Sender {

        override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
            return interceptor.invoke(nextSender, requestBuilder)
        }
    }

    private class DefaultSender(
        private val maxSendCount: Int,
        private val client: HttpClient
    ) : Sender {
        private var sentCount: Int = 0
        private var currentCall: HttpClientCall? = null

        override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
            currentCall?.cancel()

            if (sentCount >= maxSendCount) {
                throw SendCountExceedException(
                    "Max send count $maxSendCount exceeded. Consider increasing the property " +
                        "maxSendCount if more is required."
                )
            }

            sentCount++
            val sendResult = client.sendPipeline.execute(
                requestBuilder,
                requestBuilder.body
            )

            val call = sendResult as? HttpClientCall
                ?: error("Failed to execute send pipeline. Expected [HttpClientCall], but received $sendResult")

            currentCall = call
            return call
        }
    }
}

/**
 * Thrown when too many actual requests were sent during a client call.
 * It could be caused by infinite or too long redirect sequence.
 * Maximum number of requests is limited by [HttpSend.maxSendCount]
 */
public class SendCountExceedException(message: String) : IllegalStateException(message)
