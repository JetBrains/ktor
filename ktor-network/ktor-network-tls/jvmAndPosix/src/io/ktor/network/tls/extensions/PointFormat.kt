/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import kotlin.native.concurrent.*

/**
 * Elliptic curve point format
 * @property code numeric point format code
 */
public enum class PointFormat(public val code: Byte) {
    /**
     * Curve point is not compressed
     */
    UNCOMPRESSED(0),

    /**
     * Point is compressed according to ANSI X9.62
     */
    ANSIX962_COMPRESSED_PRIME(1),

    /**
     * Point is compressed according to ANSI X9.62
     */
    ANSIX962_COMPRESSED_CHAR2(2)
}

/**
 * List of supported curve point formats
 */

public val SupportedPointFormats: List<PointFormat> = listOf(
    PointFormat.UNCOMPRESSED,
    PointFormat.ANSIX962_COMPRESSED_PRIME,
    PointFormat.ANSIX962_COMPRESSED_CHAR2
)
