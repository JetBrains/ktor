/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.http.content.*

/**
 * An HTTP/2 push builder interface.
 *
 * @property url push URL
 * @property headers request headers
 * @property method request method
 * @property versions request versions (last modification date, etag, and so on)
 */
@UseHttp2Push
public interface ResponsePushBuilder {
    public val url: URLBuilder
    public val headers: HeadersBuilder
    public var method: HttpMethod
    public val versions: MutableList<Version>
}
