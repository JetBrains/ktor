/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.autohead

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.util.*

/**
 * A plugin that provides the ability to automatically respond to a `HEAD` request for every route that has a `GET` defined.
 * You can use `AutoHeadResponse` to avoid creating a separate `head` handler if you need to somehow process a response
 * on the client before getting the actual content.
 * You can learn more from [AutoHeadResponse](https://ktor.io/docs/autoheadresponse.html).
 */
public val AutoHeadResponse: ApplicationPlugin<Unit> = createApplicationPlugin("AutoHeadResponse") {
    onCall { call ->
        if (call.request.local.method != HttpMethod.Head) return@onCall
        call.mutableOriginConnectionPoint.method = HttpMethod.Get
    }

    on(ResponseBodyReadyForSend) { call, content ->
        if (call.request.local.method != HttpMethod.Head) return@on
        if (content is OutgoingContent.NoContent) return@on
        if (content is OutgoingContent.ReadChannelContent) content.readFrom().cancel(null)
        transformBodyTo(HeadResponse(content))
    }
}

private class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode? get() = original.status
    override val contentType: ContentType? get() = original.contentType
    override val contentLength: Long? get() = original.contentLength
    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
    override val headers get() = original.headers
}
