/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import io.ktor.network.tls.*
import kotlinx.io.*

public enum class TLSExtensionType(public val code: Short) {
    SERVER_NAME(code = 0),
    MAX_FRAGMENT_LENGTH(code = 1),
    CLIENT_CERTIFICATE_URL(code = 2),
    TRUSTED_CA_KEYS(code = 3),
    TRUNCATED_HMAC(code = 4),
    STATUS_REQUEST(code = 5),

    ELLIPTIC_CURVES(code = 10),
    EC_POINT_FORMAT(code = 11),
    SIGNATURE_ALGORITHMS(code = 13);

    public companion object {
        public fun byCode(code: Int): TLSExtensionType =
            entries.find { it.code == code.toShort() } ?: throw TLSException(
                "Unknown server hello extension type: $code"
            )
    }
}

internal class TLSExtension(
    val type: TLSExtensionType,
    val length: Int,
    val packet: Source
)
