/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import kotlin.native.concurrent.*

/**
 * Named curves for Elliptic Curves.
 * @property code curve numeric code
 * @property fieldSize curve size in bits, affects key and signature size, performance and security strength
 */
@Suppress("KDocMissingDocumentation", "EnumEntryName")
public enum class NamedCurve(public val code: Short, public val fieldSize: Int) {
    sect163k1(1, fieldSize = 163),
    sect163r1(2, fieldSize = 163),
    sect163r2(3, fieldSize = 163),
    sect193r1(4, fieldSize = 193),
    sect193r2(5, fieldSize = 193),
    sect233k1(6, fieldSize = 233),
    sect233r1(7, fieldSize = 233),
    sect239k1(8, fieldSize = 239),
    sect283k1(9, fieldSize = 283),
    sect283r1(10, fieldSize = 283),
    sect409k1(11, fieldSize = 409),
    sect409r1(12, fieldSize = 409),
    sect571k1(13, fieldSize = 571),
    sect571r1(14, fieldSize = 571),
    secp160k1(15, fieldSize = 160),
    secp160r1(16, fieldSize = 160),
    secp160r2(17, fieldSize = 160),
    secp192k1(18, fieldSize = 192),
    secp192r1(19, fieldSize = 192),
    secp224k1(20, fieldSize = 224),
    secp224r1(21, fieldSize = 224),
    secp256k1(22, fieldSize = 256),
    secp256r1(23, fieldSize = 256),
    secp384r1(24, fieldSize = 384),
    secp521r1(25, fieldSize = 521);

    public companion object {
        /**
         * Find named curve by numeric [code]
         * @return [NamedCurve] instance or `null`
         */
        public fun fromCode(code: Short): NamedCurve? = entries.find { it.code == code }
    }
}

/**
 * List of supported named curves
 */

public val SupportedNamedCurves: List<NamedCurve> = listOf(
    NamedCurve.secp256r1,
    NamedCurve.secp384r1
)
