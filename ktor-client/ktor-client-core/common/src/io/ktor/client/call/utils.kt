/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import io.ktor.http.*
import io.ktor.http.content.*

public class UnsupportedContentTypeException(content: OutgoingContent) :
    IllegalStateException("Failed to write body: ${content::class}")

@Suppress("KDocMissingDocumentation", "UNUSED")
public class UnsupportedUpgradeProtocolException(
    url: Url
) : IllegalArgumentException("Unsupported upgrade protocol exception: $url")

internal fun checkContentLength(contentLength: Long?, bodySize: Long, method: HttpMethod) {
    if (contentLength == null || contentLength < 0 || method == HttpMethod.Head) return

    if (contentLength != bodySize) {
        throw IllegalStateException(
            "Content-Length mismatch: expected $contentLength bytes, but received $bodySize bytes"
        )
    }
}
