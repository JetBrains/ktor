/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.*
import io.ktor.network.tls.extensions.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import java.io.*
import java.net.*
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.time.Duration.Companion.days

internal data class CertificateInfo(
    val certificate: Certificate,
    val keys: KeyPair,
    val password: String,
    val issuerCertificate: Certificate?,
)

/**
 * Builder for certificate
 */
public class CertificateBuilder internal constructor() {
    /**
     * Certificate hash algorithm
     */
    public var hash: HashAlgorithm = HashAlgorithm.SHA1

    /**
     * Certificate signature algorithm
     */
    public var sign: SignatureAlgorithm = SignatureAlgorithm.RSA

    /**
     * Certificate password (required)
     */
    public lateinit var password: String

    /**
     * The subject of the certificate, owner of the generated public key that we certify.
     */
    public var subject: X500Principal = DEFAULT_PRINCIPAL

    /**
     * Number of days the certificate is valid
     */
    public var daysValid: Long = 3

    /**
     * Certificate key size in bits
     */
    public var keySizeInBits: Int = 1024

    /**
     * The usage for the generated key.
     *
     * This determines the extensions that should be written in the certificate, such as [OID.ExtKeyUsage] (server or
     * client authentication), or [OID.BasicConstraints] to use the key as CA.
     */
    public var keyType: KeyType = KeyType.Server

    /**
     * Domains for which this certificate is valid (only relevant for [KeyType.Server], ignored for other key types).
     */
    public var domains: List<String> = listOf("localhost")

    /**
     * IP addresses for which this certificate is valid (only relevant for [KeyType.Server], ignored for other key types).
     */
    public var ipAddresses: List<InetAddress> = listOf(Inet4Address.getByName("127.0.0.1"))

    private var issuer: CertificateIssuer? = null

    private data class CertificateIssuer(
        val name: X500Principal,
        val keyPair: KeyPair,
        val keyCertificate: Certificate,
    )

    /**
     * Defines an issuer for this certificate, so it is not self-signed.
     *
     * The certificate will be signed with the given [issuerKeyPair], certified by the given [issuerKeyCertificate].
     * The issuer's name is taken from the provided certificate.
     *
     * If this method is not called, this certificate will be self-signed by the subject (with the same generated key
     * as the one being certified).
     */
    public fun signWith(
        issuerKeyPair: KeyPair,
        issuerKeyCertificate: X509Certificate,
    ) {
        issuer = CertificateIssuer(
            // the subject of the given certificate is the issuer of the certificate we're creating
            name = issuerKeyCertificate.subjectX500Principal,
            keyPair = issuerKeyPair,
            keyCertificate = issuerKeyCertificate,
        )
    }

    /**
     * Defines an issuer for this certificate, so it is not self-signed.
     *
     * The certificate will be signed with the given [issuerKeyPair] in the name of [issuerName], certified by the
     * given [issuerKeyCertificate].
     *
     * If this method is not called, this certificate will be self-signed by the subject (with the same generated key
     * as the one being certified).
     */
    public fun signWith(
        issuerKeyPair: KeyPair,
        issuerKeyCertificate: Certificate,
        issuerName: X500Principal,
    ) {
        issuer = CertificateIssuer(name = issuerName, keyPair = issuerKeyPair, keyCertificate = issuerKeyCertificate)
    }

    internal fun build(): CertificateInfo {
        val algorithm = HashAndSign(hash, sign)
        val keys = KeyPairGenerator.getInstance(keysGenerationAlgorithm(algorithm.name)).apply {
            initialize(keySizeInBits)
        }.genKeyPair()!!

        val cert = generateX509Certificate(
            issuer = issuer?.name ?: subject,
            subject = subject,
            publicKey = keys.public,
            signerKeyPair = issuer?.keyPair ?: keys,
            algorithm = algorithm.name,
            validityDuration = daysValid.days,
            keyType = keyType,
            domains = domains,
            ipAddresses = ipAddresses,
        )
        return CertificateInfo(cert, keys, password, issuer?.keyCertificate)
    }
}

/**
 * Builder for key store
 */
public class KeyStoreBuilder internal constructor() {
    private val certificates = mutableMapOf<String, CertificateInfo>()

    /**
     * Generate a certificate and append to the key store.
     * If there is a certificate with the same [alias] then it will be replaced
     */
    public fun certificate(alias: String, block: CertificateBuilder.() -> Unit) {
        certificates[alias] = CertificateBuilder().apply(block).build()
    }

    internal fun build(): KeyStore {
        val store = KeyStore.getInstance(KeyStore.getDefaultType())!!
        store.load(null, null)

        certificates.forEach { (alias, info) ->
            val (certificate, keys, password, issuerCertificate) = info
            val certChain = listOfNotNull(certificate, issuerCertificate).toTypedArray()
            store.setKeyEntry(alias, keys.private, password.toCharArray(), certChain)
        }

        return store
    }
}

/**
 * Create a keystore and configure it in [block] function
 */
public fun buildKeyStore(block: KeyStoreBuilder.() -> Unit): KeyStore = KeyStoreBuilder().apply(block).build()

/**
 * Save [KeyStore] to [output] file with the specified [password]
 */
public fun KeyStore.saveToFile(output: File, password: String) {
    output.parentFile?.mkdirs()

    output.outputStream().use {
        store(it, password.toCharArray())
    }
}
