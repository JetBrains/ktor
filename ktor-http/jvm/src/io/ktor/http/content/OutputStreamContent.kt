/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*

/**
 * [OutgoingContent] to respond with [OutputStream].
 * The stream would be automatically closed after [body] finish.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.OutputStreamContent)
 */
public class OutputStreamContent(
    private val body: suspend OutputStream.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null,
    override val contentLength: Long? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withBlocking {
            // use block should be inside because closing OutputStream is blocking as well
            // and should not be invoked in a epoll/kqueue/reactor thread
            channel.toOutputStream().use { stream ->
                stream.body()
            }
        }
    }
}
