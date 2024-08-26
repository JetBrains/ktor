/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*

/**
 * @return `true` if an http upgrade is expected according to request [method], [upgrade] header value and
 * parsed [connectionOptions]
 */
public fun expectHttpUpgrade(
    method: HttpMethod,
    upgrade: CharSequence?,
    connectionOptions: ConnectionOptions?
): Boolean = method == HttpMethod.Get &&
    upgrade != null &&
    connectionOptions?.upgrade == true

/**
 * @return `true` if an http upgrade is expected according to [request]
 */
public fun expectHttpUpgrade(request: Request): Boolean = expectHttpUpgrade(
    request.method,
    request.headers["Upgrade"],
    ConnectionOptions.parse(request.headers["Connection"])
)

/**
 * @return `true` if request or response with the specified parameters could have a body
 */
public fun expectHttpBody(
    method: HttpMethod,
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    @Suppress("UNUSED_PARAMETER") contentType: CharSequence?
): Boolean {
    if (transferEncoding != null) {
        // verify header value
        isTransferEncodingChunked(transferEncoding)
        return true
    }
    if (contentLength != -1L) return contentLength > 0L

    if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) return false
    if (connectionOptions?.close == true) return true

    return false
}

/**
 * @return `true` if request or response with the specified parameters could have a body
 */
public fun expectHttpBody(request: Request): Boolean = expectHttpBody(
    request.method,
    request.headers["Content-Length"]?.parseDecLong() ?: -1,
    request.headers["Transfer-Encoding"],
    ConnectionOptions.parse(request.headers["Connection"]),
    request.headers["Content-Type"]
)

/**
 * Parse HTTP request or response body using [contentLength], [transferEncoding] and [connectionOptions]
 * writing it to [out].
 * Usually doesn't fail but closing [out] channel with error.
 *
 * @param contentLength from the corresponding header or -1
 * @param transferEncoding header or `null`
 * @param
 */
public suspend fun parseHttpBody(
    version: HttpProtocolVersion?,
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    if (transferEncoding != null && isTransferEncodingChunked(transferEncoding)) {
        return decodeChunked(input, out)
    }

    if (contentLength != -1L) {
        input.copyTo(out, contentLength)
        return
    }

    if (connectionOptions?.close == true || (connectionOptions == null && version == HttpProtocolVersion.HTTP_1_0)) {
        input.copyTo(out, Long.MAX_VALUE)
        return
    }

    val cause = IllegalStateException(
        """
            Failed to parse request body: request body length should be specified,
            chunked transfer encoding should be used or
            keep-alive should be disabled (connection: close)
        """.trimIndent()
    )

    out.close(cause)
}

/**
 * Parse HTTP request or response body using [contentLength], [transferEncoding] and [connectionOptions]
 * writing it to [out].
 * Usually doesn't fail but closing [out] channel with error.
 *
 * @param contentLength from the corresponding header or -1
 * @param transferEncoding header or `null`
 * @param
 */
@Deprecated(
    "Please use method with version parameter",
    level = DeprecationLevel.ERROR
)
public suspend fun parseHttpBody(
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    parseHttpBody(null, contentLength, transferEncoding, connectionOptions, input, out)
}

/**
 * Parse HTTP request or response body using request/response's [headers]
 * writing it to [out]. Usually doesn't fail but closing [out] channel with error.
 */
public suspend fun parseHttpBody(
    headers: HttpHeadersMap,
    input: ByteReadChannel,
    out: ByteWriteChannel
): Unit = parseHttpBody(
    null,
    headers["Content-Length"]?.parseDecLong() ?: -1,
    headers["Transfer-Encoding"],
    ConnectionOptions.parse(headers["Connection"]),
    input,
    out
)

private fun isTransferEncodingChunked(transferEncoding: CharSequence): Boolean {
    if (transferEncoding.equalsLowerCase(other = "chunked")) {
        return true
    }
    if (transferEncoding.equalsLowerCase(other = "identity")) {
        return false
    }

    var chunked = false
    transferEncoding.split(",").forEach {
        when (val name = it.trim().lowercase()) {
            "chunked" -> {
                if (chunked) {
                    throw IllegalArgumentException("Double-chunked TE is not supported: $transferEncoding")
                }
                chunked = true
            }

            "identity" -> {
                // ignore this token
            }

            else -> throw IllegalArgumentException("Unsupported transfer encoding $name")
        }
    }

    return chunked
}
