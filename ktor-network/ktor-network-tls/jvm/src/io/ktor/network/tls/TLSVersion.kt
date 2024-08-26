/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlin.enums.*

/**
 * TLS version
 * @property code numeric TLS version code
 */
public enum class TLSVersion(public val code: Int) {
    SSL3(0x0300),
    TLS10(0x0301),
    TLS11(0x0302),
    TLS12(0x0303);

    public companion object {
        private val byOrdinal: List<TLSVersion> = entries

        /**
         * Find version instance by its numeric [code] or fail
         */
        public fun byCode(code: Int): TLSVersion = when (code) {
            in 0x0300..0x0303 -> byOrdinal[code - 0x0300]
            else -> throw IllegalArgumentException("Invalid TLS version code $code")
        }
    }
}
