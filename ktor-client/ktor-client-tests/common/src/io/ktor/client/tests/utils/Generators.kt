/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*

fun makeArray(size: Int): ByteArray = ByteArray(size) { it.toByte() }

fun makeString(size: Int): String = CharArray(size) { it.toChar() }.concatToString().encodeBase64().take(size)

suspend fun List<PartData>.makeString(): String = buildString {
    val list = this@makeString
    list.forEach {
        append("${it.name!!}\n")
        val content = when (it) {
            is PartData.FileItem -> filenameContentTypeAndContentString(it.provider, it.headers)
            is PartData.FormItem -> it.value
            is PartData.BinaryItem -> filenameContentTypeAndContentString(
                { ByteReadChannel(it.provider().readByteArray()) },
                it.headers
            )

            is PartData.BinaryChannelItem -> filenameContentTypeAndContentString(it.provider, it.headers)
        }

        append(content)
    }
}

private suspend fun filenameContentTypeAndContentString(provider: () -> ByteReadChannel, headers: Headers): String {
    val dispositionHeader: String = headers.getAll(HttpHeaders.ContentDisposition)!!.joinToString(";")
    val disposition: ContentDisposition = ContentDisposition.parse(dispositionHeader)
    val filename: String = disposition.parameter("filename") ?: ""
    val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ""
    val content: String = provider().readRemaining().readText(Charsets.ISO_8859_1)
    return "$filename$contentType$content"
}
