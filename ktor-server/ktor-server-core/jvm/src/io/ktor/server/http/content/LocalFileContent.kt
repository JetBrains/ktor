/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*
import java.nio.file.*
import kotlin.io.path.*

/**
 * OutgoingContent representing a local [file] with a specified [contentType], [expires] date and [caching]
 *
 * @param file specifies the File to be served to a client
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.http.content.LocalFileContent)
 */
public class LocalFileContent(
    public val file: File,
    override val contentType: ContentType = ContentType.defaultForFile(file)
) : OutgoingContent.ReadChannelContent() {

    override val contentLength: Long get() = file.length()

    init {
        if (!file.exists()) {
            throw IOException("No such file ${file.absolutePath}")
        } else {
            val lastModifiedVersion = file.lastModified()
            versions += LastModifiedVersion(lastModifiedVersion)
        }
    }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so engine implementation can choose
    override fun readFrom(): ByteReadChannel = file.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = file.readChannel(range.first, range.last)
}

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.http.content.LocalFileContent)
 */
public fun LocalFileContent(
    baseDir: File,
    relativePath: String,
    contentType: ContentType = ContentType.defaultForFilePath(relativePath)
): LocalFileContent = LocalFileContent(baseDir.combineSafe(relativePath), contentType)

/**
 * Creates an instance of [LocalPathContent] for a path designated by [relativePath] in a [baseDir]
 */
@Deprecated(
    "Use LocalPathContent instead",
    ReplaceWith("LocalPathContent(baseDir, relativePath, contentType)", "io.ktor.server.http.content.LocalPathContent")
)
public fun LocalFileContent(
    baseDir: Path,
    relativePath: Path,
    contentType: ContentType = ContentType.defaultForPath(relativePath)
): LocalPathContent = LocalPathContent(baseDir.combineSafe(relativePath), contentType)

/**
 * Creates an instance of [LocalPathContent] for a path designated by [relativePath] in a [baseDir]
 */
public fun LocalPathContent(
    baseDir: Path,
    relativePath: Path,
    contentType: ContentType = ContentType.defaultForPath(relativePath)
): LocalPathContent = LocalPathContent(baseDir.combineSafe(relativePath), contentType)

public class LocalPathContent(
    public val path: Path,
    override val contentType: ContentType = ContentType.defaultForFileExtension(path.extension)
) : OutgoingContent.ReadChannelContent() {

    override val contentLength: Long get() = Files.size(path)

    init {
        if (!Files.exists(path)) {
            throw IOException("No such path $path")
        } else {
            val lastModifiedVersion = Files.getLastModifiedTime(path)
            versions += LastModifiedVersion(lastModifiedVersion)
        }
    }

    override fun readFrom(): ByteReadChannel = path.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = path.readChannel(range.first, range.last)
}
