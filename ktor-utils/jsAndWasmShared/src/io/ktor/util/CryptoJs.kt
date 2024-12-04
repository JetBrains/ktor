/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

/**
 * Generates a nonce string.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.generateNonce)
 */
public actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES).toJsArray()
    _crypto.getRandomValues(buffer)
    return hex(buffer.toByteArray())
}

/**
 * Create [Digest] from specified hash [name].
 */
public actual fun Digest(name: String): Digest = object : Digest {
    private val state = mutableListOf<ByteArray>()
    override fun plusAssign(bytes: ByteArray) {
        state += bytes
    }

    override fun reset() {
        state.clear()
    }

    override suspend fun build(): ByteArray {
        val snapshot = state.reduce { a, b -> a + b }.toJsArray()
        val digestBuffer = _crypto.subtle.digest(name, snapshot).awaitBuffer()
        val digestView = DataView(digestBuffer)
        return ByteArray(digestView.byteLength) { digestView.getUint8(it) }
    }
}

// Variable is renamed to `_crypto` so it wouldn't clash with existing `crypto` variable.
// JS IR backend doesn't reserve names accessed inside js("") calls
private val _crypto: Crypto = js("(globalThis ? globalThis.crypto : (window.crypto || window.msCrypto))")

private external class Crypto {
    val subtle: SubtleCrypto

    fun getRandomValues(array: Int8Array)
}

private external class SubtleCrypto {
    fun digest(algoName: String, buffer: Int8Array): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray = Sha1().digest(bytes)
