/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.cipher

import io.ktor.network.tls.*
import java.nio.*

internal interface TLSCipher {

    fun encrypt(record: TLSRecord): TLSRecord

    fun decrypt(record: TLSRecord): TLSRecord

    companion object {
        fun fromSuite(suite: CipherSuite, keyMaterial: ByteArray): TLSCipher =
            when (suite.cipherType) {
                CipherType.GCM -> GCMCipher(suite, keyMaterial)
                CipherType.CBC -> CBCCipher(suite, keyMaterial)
            }
    }
}

internal fun ByteArray.set(offset: Int, data: Long) {
    for (idx in 0..7) {
        this[idx + offset] = (data ushr (7 - idx) * 8).toByte()
    }
}

internal fun ByteArray.set(offset: Int, data: Short) {
    for (idx in 0..1) {
        this[idx + offset] = (data.toInt() ushr (1 - idx) * 8).toByte()
    }
}

internal val EmptyByteBuffer: ByteBuffer = ByteBuffer.allocate(0)
