/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")
@file:Suppress("FunctionName")

package io.ktor.util

import kotlinx.coroutines.*
import java.security.*

/**
 * Create a digest function with the specified [algorithm] and [salt] provider.
 * @param algorithm digest algorithm name
 * @param salt a function computing a salt for a particular hash input value
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.getDigestFunction)
 */
public fun getDigestFunction(algorithm: String, salt: (value: String) -> String): (String) -> ByteArray = { e ->
    getDigest(e, algorithm, salt)
}

private fun getDigest(text: String, algorithm: String, salt: (String) -> String): ByteArray =
    with(MessageDigest.getInstance(algorithm)) {
        update(salt(text).toByteArray())
        digest(text.toByteArray())
    }

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA1").digest(bytes)

/**
 * Create [Digest] from specified hash [name].
 */
public actual fun Digest(name: String): Digest = DigestImpl(MessageDigest.getInstance(name))

@JvmInline
private value class DigestImpl(val delegate: MessageDigest) : Digest {
    override fun plusAssign(bytes: ByteArray) {
        delegate.update(bytes)
    }

    override fun reset() {
        delegate.reset()
    }

    override suspend fun build(): ByteArray = delegate.digest()
}

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
public actual fun generateNonce(): String {
    val nonce = seedChannel.tryReceive().getOrNull()
    if (nonce != null) return nonce

    return generateNonceBlocking()
}

private fun generateNonceBlocking(): String {
    ensureNonceGeneratorRunning()
    return runBlocking {
        seedChannel.receive()
    }
}
