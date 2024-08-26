/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

/**
 * TLS alert level
 * @property code alert numeric code
 */
public enum class TLSAlertLevel(public val code: Int) {
    /**
     * alert warning level
     */
    WARNING(1),

    /**
     * alert level fatal so the session most likely will be discarded
     */
    FATAL(2);

    public companion object {
        private val byCode = Array(256) { idx -> entries.firstOrNull { it.code == idx } }

        /**
         * Find alert level by its numeric [code] or fail
         */
        public fun byCode(code: Int): TLSAlertLevel = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}

/**
 * TLS alert types with codes
 * @property code numeric alert code
 */
@Suppress("KDocMissingDocumentation", "EnumEntryName")
public enum class TLSAlertType(public val code: Int) {
    DecryptionFailed_RESERVED(21),
    CloseNotify(0),
    UnexpectedMessage(10),
    BadRecordMac(20),
    RecordOverflow(22),
    DecompressionFailure(30),
    HandshakeFailure(40),
    NoCertificate_RESERVED(41),
    BadCertificate(42),
    UnsupportedCertificate(43),
    CertificateRevoked(44),
    CertificateExpired(45),
    CertificateUnknown(46),
    IllegalParameter(47),
    UnknownCa(48),
    AccessDenied(49),
    DecodeError(50),
    DecryptError(51),

    ExportRestriction_RESERVED(60),
    ProtocolVersion(70),
    InsufficientSecurity(71),
    InternalError(80),
    UserCanceled(90),
    NoRenegotiation(100),
    UnsupportedExtension(110);

    public companion object {
        private val byCode = Array(256) { idx -> entries.firstOrNull { it.code == idx } }

        /**
         * Find TLS alert instance by its numeric [code] or fail
         */
        public fun byCode(code: Int): TLSAlertType = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}
