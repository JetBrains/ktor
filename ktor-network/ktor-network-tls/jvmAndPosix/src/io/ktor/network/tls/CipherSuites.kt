/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.errors.*
import kotlinx.io.IOException

/**
 * TLS secret key exchange type.
 */
public enum class SecretExchangeType(public val jvmName: String) {
    /**
     * Elliptic Curve Diffie-Hellman Exchange.
     */
    ECDHE("ECDHE_ECDSA"),

    /**
     * RSA key exchange.
     */
    RSA("RSA")
}

/**
 * Cipher type.
 */
public enum class CipherType {
    /**
     * Galois/Counter Mode.
     * See also: https://en.wikipedia.org/wiki/Galois/Counter_Mode
     */
    GCM,

    /**
     * Cipher Block Chaining.
     * See also: https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Cipher_Block_Chaining_(CBC)
     */
    CBC
}

/**
 * Represents a TLS cipher suite
 *
 * @property code numeric cipher suite code
 * @property name cipher suite name
 * @property openSSLName for this suite that is used in openssl
 * @property exchangeType secret exchange type (ECDHE or RSA)
 * @property jdkCipherName for this suite that is used in JDK
 * @property keyStrength in bits
 * @property fixedIvLength fixed input vector length in bytes
 * @property ivLength input vector length in bytes
 * @property cipherTagSizeInBytes tag size in bytes
 * @property macName message authentication algorithm name
 * @property macStrength message authentication algorithm strength in bits
 * @property hash algorithm
 * @property signatureAlgorithm
 * @property keyStrengthInBytes key strength in bytes ( = `[keyStrength] / 8`)
 * @property macStrengthInBytes message authentication algorithm strength in bytes ( = `[macStrength] / 8`)
 * @property cipherType type of cipher to use
 */
public data class CipherSuite(
    val code: Short,
    val name: String,
    val openSSLName: String,
    val exchangeType: SecretExchangeType,
    val jdkCipherName: String,
    val keyStrength: Int,
    val fixedIvLength: Int,
    val ivLength: Int, // SecurityParameters.record_iv_length + SecurityParameters.fixed_iv_length rfc5246
    val cipherTagSizeInBytes: Int,
    val macName: String,
    val macStrength: Int,
    val hash: HashAlgorithm,
    val signatureAlgorithm: SignatureAlgorithm,
    val cipherType: CipherType = CipherType.GCM
) {
    val keyStrengthInBytes: Int = keyStrength / 8
    val macStrengthInBytes: Int = macStrength / 8
}

/**
 * CIO cipher suites collection
 * https://www.ietf.org/rfc/rfc5289.txt
 * https://tools.ietf.org/html/rfc5288#section-3
 */
@Suppress("KDocMissingDocumentation", "PublicApiImplicitType", "MemberVisibilityCanBePrivate")
public object CIOCipherSuites {
    public val TLS_RSA_WITH_AES_128_GCM_SHA256: CipherSuite = CipherSuite(
        0x009c, "TLS_RSA_WITH_AES_128_GCM_SHA256", "AES128-GCM-SHA256",
        SecretExchangeType.RSA, "AES/GCM/NoPadding",
        128, 4, 12, 16,
        "AEAD", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA
    )

    public val ECDHE_ECDSA_AES256_SHA384: CipherSuite = CipherSuite(
        0xc02c.toShort(), "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "ECDHE-ECDSA-AES256-GCM-SHA384",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        256, 4, 12, 16, "AEAD", 0,
        HashAlgorithm.SHA384, SignatureAlgorithm.ECDSA
    )

    public val ECDHE_ECDSA_AES128_SHA256: CipherSuite = CipherSuite(
        0xc02b.toShort(), "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "ECDHE-ECDSA-AES128-GCM-SHA256",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        128, 4, 12, 16, "AEAD", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.ECDSA
    )

    public val ECDHE_RSA_AES256_SHA384: CipherSuite = CipherSuite(
        0xc030.toShort(), "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "ECDHE-RSA-AES256-GCM-SHA384",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        256, 4, 12, 16, "AEAD", 0,
        HashAlgorithm.SHA384, SignatureAlgorithm.RSA
    )

    public val ECDHE_RSA_AES128_SHA256: CipherSuite = CipherSuite(
        0xc02f.toShort(), "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "ECDHE-RSA-AES128-GCM-SHA256",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        128, 4, 12, 16, "AEAD", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA
    )

    public val TLS_RSA_WITH_AES256_CBC_SHA: CipherSuite = CipherSuite(
        0x0035, "TLS_RSA_WITH_AES_256_CBC_SHA", "AES-256-CBC-SHA",
        SecretExchangeType.RSA, "AES/CBC/NoPadding",
        256, 16, 32 + 16, 20,
        "HmacSHA1", 20 * 8,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA, CipherType.CBC
    )

    public val TLS_RSA_WITH_AES128_CBC_SHA: CipherSuite = CipherSuite(
        0x002F, "TLS_RSA_WITH_AES_128_CBC_SHA", "AES-128-CBC-SHA",
        SecretExchangeType.RSA, "AES/CBC/NoPadding",
        128, 16, 32 + 16, 20,
        "HmacSHA1", 20 * 8,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA, CipherType.CBC
    )

    /**
     * List of suites supported by current platform
     */
    public val SupportedSuites: List<CipherSuite> = listOf(
        ECDHE_ECDSA_AES256_SHA384,
        ECDHE_RSA_AES256_SHA384,
        ECDHE_ECDSA_AES128_SHA256,
        ECDHE_RSA_AES128_SHA256,
        TLS_RSA_WITH_AES_128_GCM_SHA256,
        TLS_RSA_WITH_AES256_CBC_SHA,
        TLS_RSA_WITH_AES128_CBC_SHA
    ).filter { it.isSupported() }
}

internal expect fun CipherSuite.isSupported(): Boolean

public class TLSException(message: String, cause: Throwable? = null) : IOException(message, cause)
