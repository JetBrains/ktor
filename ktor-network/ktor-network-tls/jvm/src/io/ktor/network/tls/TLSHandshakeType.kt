/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*

/**
 * TLS handshake record type
 * @property code numeric type code
 */

public enum class TLSHandshakeType(public val code: Int) {
    HelloRequest(0x00),
    ClientHello(0x01),
    ServerHello(0x02),
    Certificate(0x0b),
    ServerKeyExchange(0x0c),
    CertificateRequest(0x0d),
    ServerDone(0x0e),
    CertificateVerify(0x0f),
    ClientKeyExchange(0x10),
    Finished(0x14);

    public companion object {
        private val byCode = Array(256) { idx -> entries.firstOrNull { it.code == idx } }

        /**
         * Find handshake type instance by its numeric [code] or fail
         */
        public fun byCode(code: Int): TLSHandshakeType = when (code) {
            in 0..0xff -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS handshake type code: $code")
    }
}

/**
 * Server key exchange type with it's [code]
 * @property code numeric exchange type code
 */

public enum class ServerKeyExchangeType(public val code: Int) {
    ExplicitPrime(1),
    ExplicitChar(2),
    NamedCurve(3);

    public companion object {
        private val byCode = Array(256) { idx -> entries.firstOrNull { it.code == idx } }

        /**
         * Find an instance of [ServerKeyExchangeType] by its numeric code or fail
         */
        public fun byCode(code: Int): ServerKeyExchangeType {
            val result = if (code in 0..0xff) byCode[code] else null
            return result ?: throw IllegalArgumentException("Invalid TLS ServerKeyExchange type code: $code")
        }
    }
}

internal class TLSServerHello(
    val version: TLSVersion,
    val serverSeed: ByteArray,
    val sessionId: ByteArray,
    suite: Short,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = emptyList()
) {
    val cipherSuite: CipherSuite = CIOCipherSuites.SupportedSuites.find { it.code == suite }
        ?: error("Server cipher suite is not supported: $suite")

    val hashAndSignAlgorithms: List<HashAndSign>

    init {
        val algorithms = mutableListOf<HashAndSign>()
        extensions.forEach {
            when (it.type) {
                TLSExtensionType.SIGNATURE_ALGORITHMS -> {
                    algorithms += it.packet.parseSignatureAlgorithms()
                }
                else -> {
                }
            }
        }

        hashAndSignAlgorithms = algorithms
    }
}
