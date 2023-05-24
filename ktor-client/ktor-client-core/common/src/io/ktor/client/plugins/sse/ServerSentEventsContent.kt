/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.http.*
import io.ktor.http.content.*

internal class ServerSentEventsContent(private val reconnectionTimeMillis: Long) : OutgoingContent.NoContent() {

    override val headers: Headers = HeadersBuilder().apply {
        append(HttpHeaders.Accept, ContentType.Text.EventStream)
        append(HttpHeaders.CacheControl, "no-store")
    }.build()

    override fun toString(): String = "ServerSentEventsContent"
}
