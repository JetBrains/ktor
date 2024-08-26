/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*

/**
 * Default outgoing content transformation
 */
public fun transformDefaultContent(call: ApplicationCall, value: Any): OutgoingContent? = when (value) {
    is OutgoingContent -> value
    is String -> {
        val contentType = call.defaultTextContentType(null)
        TextContent(value, contentType, null)
    }
    is ByteArray -> {
        ByteArrayContent(value)
    }
    is HttpStatusCode -> {
        HttpStatusCodeContent(value)
    }
    is ByteReadChannel -> object : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel = value
    }
    else -> platformTransformDefaultContent(call, value)
}

internal expect fun platformTransformDefaultContent(call: ApplicationCall, value: Any): OutgoingContent?
