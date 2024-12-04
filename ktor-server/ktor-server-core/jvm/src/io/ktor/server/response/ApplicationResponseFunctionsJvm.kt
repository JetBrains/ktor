/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import java.io.*
import java.nio.file.*

/**
 * Respond with text content writer.
 *
 * The [writer] parameter will be called later when engine is ready to produce content.
 * Provided [Writer] will be closed automatically.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.response.respondTextWriter)
 */
public suspend fun ApplicationCall.respondTextWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    writer: suspend Writer.() -> Unit
) {
    val message = WriterContent(writer, defaultTextContentType(contentType), status)
    respond(message)
}

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [OutputStream] will be closed automatically.
 */
public suspend fun ApplicationCall.respondOutputStream(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend OutputStream.() -> Unit
) {
    val message = OutputStreamContent(producer, contentType ?: ContentType.Application.OctetStream, status)
    respond(message)
}

/**
 * Responds to a client with a contents of a file with the name [fileName] in the [baseDir] folder
 */
public suspend fun ApplicationCall.respondFile(
    baseDir: File,
    fileName: String,
    configure: OutgoingContent.() -> Unit = {}
) {
    val message = LocalFileContent(baseDir, fileName).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a contents of a path designated by [relativePath] in the [baseDir] folder
 */
public suspend fun ApplicationCall.respondPath(
    baseDir: Path,
    relativePath: Path,
    configure: OutgoingContent.() -> Unit = {}
) {
    val message = LocalPathContent(baseDir, relativePath).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a contents of a [file]
 */
public suspend fun ApplicationCall.respondFile(file: File, configure: OutgoingContent.() -> Unit = {}) {
    val message = LocalFileContent(file).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a contents of a [path]
 */
public suspend fun ApplicationCall.respondPath(path: Path, configure: OutgoingContent.() -> Unit = {}) {
    val message = LocalPathContent(path).apply(configure)
    respond(message)
}

/**
 * Respond with text content writer.
 *
 * The [writer] parameter will be called later when engine is ready to produce content.
 * Provided [Writer] will be closed automatically.
 */
public suspend fun ApplicationCall.respondTextWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    contentLength: Long? = null,
    writer: suspend Writer.() -> Unit,
) {
    val message = WriterContent(writer, defaultTextContentType(contentType), status, contentLength)
    respond(message)
}

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [OutputStream] will be closed automatically.
 */
public suspend fun ApplicationCall.respondOutputStream(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    contentLength: Long? = null,
    producer: suspend OutputStream.() -> Unit
) {
    val message =
        OutputStreamContent(producer, contentType ?: ContentType.Application.OctetStream, status, contentLength)
    respond(message)
}
