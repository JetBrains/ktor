/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.Foundation.*
import kotlin.coroutines.*

@OptIn(DelicateCoroutinesApi::class)
internal class DarwinLegacyTaskHandler(
    private val requestData: HttpRequestData,
    private val callContext: CoroutineContext
) {
    val response: CompletableDeferred<HttpResponseData> = CompletableDeferred()

    private val requestTime: GMTDate = GMTDate()
    private val bodyChunks = Channel<ByteArray>(Channel.UNLIMITED)

    private val body: ByteReadChannel = GlobalScope.writer(callContext) {
        try {
            bodyChunks.consumeEach {
                channel.writeFully(it)
                channel.flush()
            }
        } catch (cause: CancellationException) {
            bodyChunks.cancel(cause)
            throw cause
        }
    }.channel

    fun receiveData(dataTask: NSURLSessionDataTask, data: NSData) {
        if (!response.isCompleted) {
            val result = dataTask.response as NSHTTPURLResponse
            response.complete(result.toResponseData(requestData))
        }

        val content = data.toByteArray()
        try {
            bodyChunks.trySend(content).isSuccess
        } catch (cause: CancellationException) {
            dataTask.cancel()
        }
    }

    fun complete(task: NSURLSessionTask, didCompleteWithError: NSError?) {
        if (didCompleteWithError != null) {
            val exception = handleNSError(requestData, didCompleteWithError)
            bodyChunks.close(exception)
            response.completeExceptionally(exception)
            return
        }

        if (!response.isCompleted) {
            val result = task.response as NSHTTPURLResponse
            response.complete(result.toResponseData(requestData))
        }

        bodyChunks.close()
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, InternalAPI::class)
    fun NSHTTPURLResponse.toResponseData(requestData: HttpRequestData): HttpResponseData {
        val status = HttpStatusCode.fromValue(statusCode.convert())
        val headers = readHeaders()
        val responseBody: Any = requestData.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(requestData, status, headers, body, requestData.body, callContext)
            ?: body

        return HttpResponseData(
            status,
            requestTime,
            headers,
            HttpProtocolVersion.HTTP_1_1,
            responseBody,
            callContext
        )
    }
}
