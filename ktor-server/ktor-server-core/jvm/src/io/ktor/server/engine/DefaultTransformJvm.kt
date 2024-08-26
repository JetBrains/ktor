/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.io.*
import java.io.*

internal actual suspend fun PipelineContext<Any, PipelineCall>.defaultPlatformTransformations(
    query: Any
): Any? {
    val channel = query as? ByteReadChannel ?: return null

    return when (call.receiveType.type) {
        InputStream::class -> receiveGuardedInputStream(channel)
        MultiPartData::class -> multiPartData(channel)
        else -> null
    }
}

@OptIn(InternalAPI::class)
internal actual fun PipelineContext<*, PipelineCall>.multiPartData(rc: ByteReadChannel): MultiPartData {
    val contentType = call.request.header(HttpHeaders.ContentType)
        ?: throw IllegalStateException("Content-Type header is required for multipart processing")

    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
    return CIOMultipartDataBase(
        coroutineContext + Dispatchers.Unconfined,
        rc,
        contentType,
        contentLength,
        call.formFieldLimit
    )
}

internal actual fun Source.readTextWithCustomCharset(charset: Charset): String =
    inputStream().reader(charset).readText()

private fun receiveGuardedInputStream(channel: ByteReadChannel): InputStream {
    return channel.toInputStream()
}
