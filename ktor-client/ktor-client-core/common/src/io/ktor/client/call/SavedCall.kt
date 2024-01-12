/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class SavedHttpCall(
    client: HttpClient,
    request: HttpRequest,
    response: HttpResponse,
    private val responseBody: ByteArray
) : HttpClientCall(client) {

    init {
        this.request = SavedHttpRequest(this, request)
        this.response = SavedHttpResponse(this, responseBody, response)
    }

    /**
     * Returns a channel with [responseBody] data.
     */
    override suspend fun getResponseContent(): ByteReadChannel {
        return ByteReadChannel(responseBody)
    }

    override val allowDoubleReceive: Boolean = true
}

internal class SavedHttpRequest(
    override val call: SavedHttpCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class SavedHttpResponse(
    override val call: SavedHttpCall,
    private val body: ByteArray,
    origin: HttpResponse
) : HttpResponse() {
    private val context = Job()

    override val status: HttpStatusCode = origin.status

    override val version: HttpProtocolVersion = origin.version

    override val requestTime: GMTDate = origin.requestTime

    override val responseTime: GMTDate = origin.responseTime

    override val headers: Headers = origin.headers

    override val coroutineContext: CoroutineContext = origin.coroutineContext + context

    @OptIn(InternalAPI::class)
    override val content: ByteReadChannel get() = ByteReadChannel(body)
}

/**
 * Fetch data for [HttpClientCall] and close the origin.
 */
@OptIn(InternalAPI::class)
public suspend fun HttpClientCall.save(): HttpClientCall {
    val responseBody = response.content.readRemaining().readBytes()

    return SavedHttpCall(client, request, response, responseBody)
}
