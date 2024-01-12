/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

@InternalAPI
public class DefaultHttpResponse(
    override val call: HttpClientCall,
    responseData: HttpResponseData
) : HttpResponse() {
    override val coroutineContext: CoroutineContext = responseData.callContext

    override val status: HttpStatusCode = responseData.statusCode

    override val version: HttpProtocolVersion = responseData.version

    override val requestTime: GMTDate = responseData.requestTime

    override val responseTime: GMTDate = responseData.responseTime

    override val content: ByteReadChannel = responseData.body as? ByteReadChannel
        ?: ByteReadChannel.Empty

    override val headers: Headers = responseData.headers
}
