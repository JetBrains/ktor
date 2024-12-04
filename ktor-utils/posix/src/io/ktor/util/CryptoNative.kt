/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Generates a nonce string 32 characters long. Could block if the system's entropy source is empty
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.generateNonce)
 */
public actual fun generateNonce(): String {
    val bytes = ByteArray(16)
    secureRandom(bytes)
    return hex(bytes)
}

internal expect fun secureRandom(bytes: ByteArray)

/**
 * Create [Digest] from specified hash [name].
 */
public actual fun Digest(name: String): Digest = error("[Digest] is not supported on Darwin")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray = Sha1().digest(bytes)
