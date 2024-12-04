/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*

/**
 * OutgoingContent representing a local [file] with a specified [contentType], [expires] date and [caching]
 *
 * @param file specifies the File to be served to a client
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.content.LocalFileContent)
 */
public class LocalFileContent(
    public val file: File,
    override val contentType: ContentType = ContentType.defaultForFile(file)
) : OutgoingContent.ReadChannelContent() {

    override val contentLength: Long get() = file.length()

    override fun readFrom(): ByteReadChannel = file.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = file.readChannel(range.first, range.last)
}

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.content.LocalFileContent)
 */
public fun LocalFileContent(
    baseDir: File,
    relativePath: String,
    contentType: ContentType = ContentType.defaultForFilePath(relativePath)
): LocalFileContent = LocalFileContent(baseDir.combineSafe(relativePath), contentType)
