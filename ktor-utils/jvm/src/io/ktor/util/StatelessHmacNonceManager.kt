/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * Stateless nonce manager implementation with HMAC verification and timeout.
 * Every nonce provided by this manager consist of a random part, timestamp and HMAC.
 * @property keySpec secret key spec for HMAC
 * @property algorithm HMAC algorithm name, `HmacSHA256` by default
 * @property timeoutMillis specifies the amount of time for a nonce to be considered valid
 * @property nonceGenerator function that produces random values
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.StatelessHmacNonceManager)
 */
public class StatelessHmacNonceManager(
    public val keySpec: SecretKeySpec,
    public val algorithm: String = "HmacSHA256",
    public val timeoutMillis: Long = 60000,
    public val nonceGenerator: () -> String = { generateNonce() }
) : NonceManager {
    /**
     * Helper constructor that makes a secret key from [key] ByteArray
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.StatelessHmacNonceManager.StatelessHmacNonceManager)
     */
    public constructor(
        key: ByteArray,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 60000,
        nonceGenerator: () -> String = { generateNonce() }
    ) : this(
        SecretKeySpec(
            key,
            algorithm
        ),
        algorithm,
        timeoutMillis,
        nonceGenerator
    )

    /**
     * MAC length in bytes
     */
    private val macLength = Mac.getInstance(algorithm).let { mac ->
        mac.init(keySpec)
        mac.macLength
    }

    override suspend fun newNonce(): String {
        val random = nonceGenerator()
        val time = System.nanoTime().toString(16).padStart(16, '0')

        val mac = hex(
            Mac.getInstance(algorithm).apply {
                init(keySpec)
                update("$random:$time".toByteArray(Charsets.ISO_8859_1))
            }.doFinal()
        )

        return "$random+$time+$mac"
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        val parts = nonce.split('+')
        if (parts.size != 3) return false
        val (random, time, mac) = parts

        if (random.length < 8) return false
        if (mac.length != macLength * 2) return false
        if (time.length != 16) return false

        val nanoTime = time.toLong(16)
        if (nanoTime + TimeUnit.MILLISECONDS.toNanos(timeoutMillis) < System.nanoTime()) return false

        val computedMac = hex(
            Mac.getInstance(algorithm).apply {
                init(keySpec)
                update("$random:$time".toByteArray(Charsets.ISO_8859_1))
            }.doFinal()
        )

        var validCount = 0
        for (i in 0 until minOf(computedMac.length, mac.length)) {
            if (computedMac[i] == mac[i]) {
                validCount++
            }
        }

        return validCount == macLength * 2
    }
}
