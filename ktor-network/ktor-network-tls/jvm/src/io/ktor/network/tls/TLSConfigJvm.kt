/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

/**
 * TLS configuration.
 * @property trustManager: Custom [X509TrustManager] to verify server authority. Use system by default.
 * @property random: [SecureRandom] to use in encryption.
 * @property certificates: list of client certificate chains with private keys.
 * @property cipherSuites: list of allowed [CipherSuite]s.
 * @property serverName: custom server name for TLS server name extension.
 */
public actual class TLSConfig(
    public val random: SecureRandom,
    public val certificates: List<CertificateAndKey>,
    public val trustManager: X509TrustManager,
    public val cipherSuites: List<CipherSuite>,
    public actual val isClient: Boolean,
    public actual val serverName: String?,
    public actual val authentication: TLSAuthenticationConfig?,
    public actual val verification: TLSVerificationConfig?,
) : Closeable {
    public val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(
                authentication?.keyManagerFactory?.keyManagers,
                verification?.trustManagerFactory?.trustManagers ?: arrayOf(trustManager),
                random
            )
        }
    }

    override fun close() {
        //NOOP
    }
}

//TODO: migrate to just trustManager and keyManager?
public actual class TLSAuthenticationConfig(
    public val keyManagerFactory: KeyManagerFactory
)

public actual class TLSVerificationConfig(
    public val trustManagerFactory: TrustManagerFactory
)

/**
 * Client certificate chain with private key.
 * @property certificateChain: client certificate chain.
 * @property key: [PrivateKey] for certificate chain.
 */
public class CertificateAndKey(public val certificateChain: Array<X509Certificate>, public val key: PrivateKey)
