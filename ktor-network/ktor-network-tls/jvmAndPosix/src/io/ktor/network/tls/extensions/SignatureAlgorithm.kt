/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import io.ktor.network.tls.*
import io.ktor.utils.io.core.*
import kotlinx.io.*

/**
 * See also: [https://www.iana.org/assignments/tls-parameters/tls-parameters.txt]
 */

/**
 * Hash algorithms
 * @property code numeric hash algorithm code
 * @property openSSLName is a name used in openssl for this algorithm
 */

public enum class HashAlgorithm(public val code: Byte, public val openSSLName: String, public val macName: String) {
    NONE(0, "", ""),
    MD5(1, "MD5", "HmacMD5"),
    SHA1(2, "SHA-1", "HmacSHA1"),
    SHA224(3, "SHA-224", "HmacSHA224"),
    SHA256(4, "SHA-256", "HmacSHA256"),
    SHA384(5, "SHA-384", "HmacSHA384"),
    SHA512(6, "SHA-512", "HmacSHA512"),

    INTRINSIC(8, "INTRINSIC", "Intrinsic");

    public companion object {
        /**
         * Find hash algorithm instance by its numeric [code]
         * @throws TLSExtension if no hash algorithm found by code
         */
        public fun byCode(code: Byte): HashAlgorithm = entries.find { it.code == code }
            ?: throw TLSException("Unknown hash algorithm: $code")
    }
}

/**
 * Signature algorithms
 * @property code numeric algorithm codes
 */

public enum class SignatureAlgorithm(public val code: Byte) {
    ANON(0),
    RSA(1),
    DSA(2),
    ECDSA(3),

    ED25519(7),
    ED448(8);

    public companion object {
        /**
         * Find signature algorithm instance by its numeric [code]
         * @throws TLSExtension if no hash algorithm found by code
         */
        public fun byCode(code: Byte): SignatureAlgorithm? = entries.find { it.code == code }
    }
}

/**
 * Hash and signature algorithm pair
 *
 * @property hash algorithm.
 * @property sign algorithm.
 * @property oid [object identifier](https://en.wikipedia.org/wiki/Object_identifier).
 */

public data class HashAndSign(val hash: HashAlgorithm, val sign: SignatureAlgorithm, val oid: OID? = null) {
    /**
     * String representation of this algorithms pair
     */
    val name: String = "${hash.name}with${sign.name}"

    public companion object
}

@Suppress("CONFLICTING_OVERLOADS")
internal fun HashAndSign(hashValue: Byte, signValue: Byte, oidValue: String? = null): HashAndSign? {
    val hash = HashAlgorithm.byCode(hashValue)
    val sign = SignatureAlgorithm.byCode(signValue) ?: return null
    val oid = oidValue?.let { OID(it) }

    return HashAndSign(hash, sign, oid)
}

/**
 * List of supported combinations of hash and signature algorithms
 */

public val SupportedSignatureAlgorithms: List<HashAndSign> = listOf(
    HashAndSign(HashAlgorithm.SHA384, SignatureAlgorithm.ECDSA, OID.ECDSAwithSHA384Encryption),
    HashAndSign(HashAlgorithm.SHA256, SignatureAlgorithm.ECDSA, OID.ECDSAwithSHA256Encryption),

    HashAndSign(HashAlgorithm.SHA512, SignatureAlgorithm.RSA, OID.RSAwithSHA512Encryption),
    HashAndSign(HashAlgorithm.SHA384, SignatureAlgorithm.RSA, OID.RSAwithSHA384Encryption),
    HashAndSign(HashAlgorithm.SHA256, SignatureAlgorithm.RSA, OID.RSAwithSHA256Encryption),
    HashAndSign(HashAlgorithm.SHA1, SignatureAlgorithm.RSA, OID.RSAwithSHA1Encryption)
)

internal fun Source.parseSignatureAlgorithms(): List<HashAndSign> {
    val length = readShort().toInt() and 0xffff

    val result = mutableListOf<HashAndSign>()
    while (remaining > 0) {
        result += readHashAndSign() ?: continue
    }

    if (remaining.toInt() != length) {
        throw TLSException("Invalid hash and sign packet size: expected $length, actual ${result.size}")
    }

    return result
}

internal fun Source.readHashAndSign(): HashAndSign? {
    val hash = readByte()
    val sign = readByte()
    return HashAndSign.byCode(hash, sign)
}

public fun HashAndSign.Companion.byCode(hash: Byte, sign: Byte): HashAndSign? {
    check(sign != SignatureAlgorithm.ANON.code) { "Anonymous signature not allowed." }

    return SupportedSignatureAlgorithms.find { it.hash.code == hash && it.sign.code == sign } ?: HashAndSign(hash, sign)
}
