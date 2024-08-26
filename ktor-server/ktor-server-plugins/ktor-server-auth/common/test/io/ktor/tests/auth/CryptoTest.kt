/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class CryptoTest {
    @Test
    fun testBase64() {
        assertEquals("AAAA", ByteArray(3).encodeBase64())
        assertEquals(ByteArray(3), "AAAA".decodeBase64Bytes())
    }

    @Test
    fun testHex() {
        assertEquals("00af", hex(byteArrayOf(0, 0xaf.toByte())))
        assertEquals(byteArrayOf(0, 0xaf.toByte()), hex("00af"))
    }

    @Test
    fun testRaw() {
        assertEquals(byteArrayOf(0x31, 0x32, 0x33), raw("123"))
    }

    private fun assertEquals(a: ByteArray, b: ByteArray) {
        fun Byte.h() = this.toString(16)
        assertEquals(a.map(Byte::h), b.map { it.h() })
    }

    private fun raw(s: String) = s.toByteArray(Charsets.UTF_8)
}
