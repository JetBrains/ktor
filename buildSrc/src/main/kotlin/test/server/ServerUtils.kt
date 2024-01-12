/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

fun makeArray(size: Int): ByteArray = ByteArray(size) { it.toByte() }

fun makeString(size: Int): String = CharArray(size) { it.toChar() }.concatToString().encodeBase64().take(size)

suspend fun PartData.makeString(): String = buildString {
    val part = this@makeString
    append("${part.name!!}\n")
    val content = when (part) {
        is PartData.FileItem -> filenameContentTypeAndContentString(part.provider, part.headers)
        is PartData.FormItem -> part.value
        is PartData.BinaryItem -> error("BinaryItem is not supported in test server")
        is PartData.BinaryChannelItem -> filenameContentTypeAndContentString(part.provider, part.headers)
    }

    append(content)
}

internal suspend fun filenameContentTypeAndContentString(provider: () -> ByteReadChannel, headers: Headers): String {
    val dispositionHeader: String = headers.getAll(HttpHeaders.ContentDisposition)!!.joinToString(";")
    val disposition: ContentDisposition = ContentDisposition.parse(dispositionHeader)
    val filename: String = disposition.parameter("filename") ?: ""
    val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ""
    val content: String = provider().readRemaining().readBytes().let { "Content of ${it.size} bytes" }
    return "$filename$contentType$content"
}
