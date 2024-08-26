/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

/**
 * TLS record type with it's numeric [code]
 * @property code numeric record type code
 */

public enum class TLSRecordType(public val code: Int) {
    ChangeCipherSpec(0x14),
    Alert(0x15),
    Handshake(0x16),
    ApplicationData(0x17);

    public companion object {
        private val byCode = Array(256) { idx -> entries.firstOrNull { it.code == idx } }

        /**
         * Find an instance of [TLSRecordType] by its numeric code or fail
         */
        public fun byCode(code: Int): TLSRecordType = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}
