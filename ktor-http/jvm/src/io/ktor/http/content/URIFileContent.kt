/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.net.*

/**
 * Represents a content that is served from the specified [uri]
 * @property uri that is used as a source
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.URIFileContent)
 */
public class URIFileContent(
    public val uri: URI,
    override val contentType: ContentType = ContentType.defaultForFilePath(uri.path),
    override val contentLength: Long? = null,
) : OutgoingContent.ReadChannelContent() {
    public constructor(url: URL, contentType: ContentType = ContentType.defaultForFilePath(url.path)) : this(
        url.toURI(),
        contentType
    )

    // TODO: use http client
    override fun readFrom(): ByteReadChannel = uri.toURL().openStream().toByteReadChannel(pool = KtorDefaultPool)
}
