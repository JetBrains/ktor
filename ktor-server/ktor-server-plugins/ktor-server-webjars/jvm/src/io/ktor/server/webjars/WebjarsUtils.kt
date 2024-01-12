/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.webjars

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.webjars.*
import java.io.*

internal fun extractWebJar(
    path: String,
    knownWebJars: Set<String>,
    locator: WebJarAssetLocator
): Pair<String, WebJarAssetLocator.WebJarInfo> {
    val firstDelimiter = if (path.startsWith("/")) 1 else 0
    val nextDelimiter = path.indexOf("/", 1)
    val webjar = if (nextDelimiter > -1) path.substring(firstDelimiter, nextDelimiter) else ""
    val partialPath = path.substring(nextDelimiter + 1)
    if (webjar !in knownWebJars) {
        throw IllegalArgumentException("jar $webjar not found")
    }
    val info = locator.allWebJars[webjar] ?: throw IllegalArgumentException("jar $webjar not found")
    return locator.getFullPath(webjar, partialPath) to info
}

internal class InputStreamContent(
    private val input: InputStream,
    override val contentType: ContentType
) : OutgoingContent.ReadChannelContent() {

    override fun readFrom(): ByteReadChannel = input.toByteReadChannel(pool = KtorDefaultPool)
}
