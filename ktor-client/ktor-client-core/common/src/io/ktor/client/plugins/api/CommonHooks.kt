/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.events.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A hook that executes first in request processing.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.SetupRequest)
 */
public object SetupRequest : ClientHook<suspend (HttpRequestBuilder) -> Unit> {
    override fun install(client: HttpClient, handler: suspend (HttpRequestBuilder) -> Unit) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            handler(context)
        }
    }
}

/**
 * A hook that can inspect response and initiate additional requests if needed.
 * Useful for handling redirects, retries, authentication, etc.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.Send)
 */
public object Send : ClientHook<suspend Send.Sender.(HttpRequestBuilder) -> HttpClientCall> {

    public class Sender internal constructor(
        private val httpSendSender: io.ktor.client.plugins.Sender,
        override val coroutineContext: CoroutineContext
    ) : CoroutineScope {
        /**
         * Continues execution of the request.
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.Send.Sender.proceed)
         */
        public suspend fun proceed(requestBuilder: HttpRequestBuilder): HttpClientCall =
            httpSendSender.execute(requestBuilder)
    }

    override fun install(client: HttpClient, handler: suspend Sender.(HttpRequestBuilder) -> HttpClientCall) {
        client.plugin(HttpSend).intercept { request ->
            handler(Sender(this, client.coroutineContext), request)
        }
    }
}

/**
 * A hook that is executed for every request, even if it's not user initiated.
 * For example, if a request results in redirect,
 * [ClientPluginBuilder.onRequest] will be executed only for the original request,
 * but this hook will be executed for both original and redirected requests.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.SendingRequest)
 */
public object SendingRequest :
    ClientHook<suspend (request: HttpRequestBuilder, content: OutgoingContent) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend (request: HttpRequestBuilder, content: OutgoingContent) -> Unit
    ) {
        client.sendPipeline.intercept(HttpSendPipeline.State) {
            handler(context, subject as OutgoingContent)
        }
    }
}

/**
 * A shortcut hook for [HttpClient.monitor] subscription.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.MonitoringEvent)
 */
public class MonitoringEvent<Param : Any, Event : EventDefinition<Param>>(private val event: Event) :
    ClientHook<(Param) -> Unit> {

    override fun install(client: HttpClient, handler: (Param) -> Unit) {
        client.monitor.subscribe(event) {
            handler(it)
        }
    }
}
