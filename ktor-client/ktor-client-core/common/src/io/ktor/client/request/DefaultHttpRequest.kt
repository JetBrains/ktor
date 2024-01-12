/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Default [HttpRequest] implementation.
 */
@InternalAPI
public open class DefaultHttpRequest(override val call: HttpClientCall, data: HttpRequestData) : HttpRequest {
    override val coroutineContext: CoroutineContext get() = call.coroutineContext

    override val method: HttpMethod = data.method

    override val url: Url = data.url

    override val content: OutgoingContent = data.body

    override val headers: Headers = data.headers

    override val attributes: Attributes = data.attributes
}
