/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

/**
 * Generates a nonce string.
 */
public actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES).toJsArray()
    when {
        PlatformUtils.IS_NODE -> _crypto.randomFillSync(buffer)
        else -> _crypto.getRandomValues(buffer)
    }
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

private fun requireCrypto(): Crypto = js("eval('require')('crypto')")
private fun windowCrypto(): Crypto = js("(window ? (window.crypto ? window.crypto : window.msCrypto) : self.crypto)")

// Variable is renamed to `_crypto` so it wouldn't clash with existing `crypto` variable.
// JS IR backend doesn't reserve names accessed inside js("") calls
private val _crypto: Crypto by lazy { // lazy because otherwise it's untestable due to evaluation order
    when {
        PlatformUtils.IS_NODE -> requireCrypto()
        else -> windowCrypto()
    }
}

private external class Crypto {
    val subtle: SubtleCrypto

    fun getRandomValues(array: Int8Array)

    fun randomFillSync(array: Int8Array)
}

private external class SubtleCrypto {
    fun digest(algoName: String, buffer: Int8Array): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray = Sha1().digest(bytes)
