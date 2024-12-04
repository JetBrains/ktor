/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import kotlin.native.concurrent.*

/**
 * Elliptic curve point format
 * @property code numeric point format code
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.network.tls.extensions.PointFormat)
 */
public enum class PointFormat(public val code: Byte) {
    /**
     * Curve point is not compressed
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.network.tls.extensions.PointFormat.UNCOMPRESSED)
     */
    UNCOMPRESSED(0),

    /**
     * Point is compressed according to ANSI X9.62
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.network.tls.extensions.PointFormat.ANSIX962_COMPRESSED_PRIME)
     */
    ANSIX962_COMPRESSED_PRIME(1),

    /**
     * Point is compressed according to ANSI X9.62
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.network.tls.extensions.PointFormat.ANSIX962_COMPRESSED_CHAR2)
     */
    ANSIX962_COMPRESSED_CHAR2(2)
}

/**
 * List of supported curve point formats
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.network.tls.extensions.SupportedPointFormats)
 */

public val SupportedPointFormats: List<PointFormat> = listOf(
    PointFormat.UNCOMPRESSED,
    PointFormat.ANSIX962_COMPRESSED_PRIME,
    PointFormat.ANSIX962_COMPRESSED_CHAR2
)
