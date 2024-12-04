/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*

/**
 * Implementation of the [OutgoingContent.ByteArrayContent] for sending array of bytes
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.ByteArrayContent)
 */
public class ByteArrayContent(
    private val bytes: ByteArray,
    override val contentType: ContentType? = null,
    override val status: HttpStatusCode? = null
) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long get() = bytes.size.toLong()

    override fun bytes(): ByteArray = bytes
}
