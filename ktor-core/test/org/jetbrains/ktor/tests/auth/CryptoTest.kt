package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.crypto.*
import org.junit.*
import javax.crypto.*
import javax.crypto.spec.*
import kotlin.test.*

class CryptoTest {
    @Test
    fun testDecryptorsWithInputVector() {
        val originalText = "Test string"
        val iv = ByteArray(16)
        val secretKey = with(KeyGenerator.getInstance("AES")) {
            init(128)
            generateKey()
        }

        val encrypted = with(Cipher.getInstance("AES/CBC/PKCS5Padding")) {
            init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            doFinal(originalText.toByteArray())
        }

        val decryptor = SimpleJavaCryptoPasswordDecryptor("AES/CBC/PKCS5Padding", secretKey.encoded, iv)

        assertEquals(originalText, decryptor.decrypt(base64(encrypted)))
    }

    @Test
    fun testDecryptorsWithNoInputVector() {
        val originalText = "Test string"
        val secretKey = with(KeyGenerator.getInstance("AES")) {
            init(128)
            generateKey()
        }

        val (encrypted, iv) = with(Cipher.getInstance("AES/CBC/PKCS5Padding")) {
            init(Cipher.ENCRYPT_MODE, secretKey)
            doFinal(originalText.toByteArray()) to iv
        }

        val decryptor = SimpleJavaCryptoPasswordDecryptor("AES/CBC/PKCS5Padding", secretKey.encoded, iv)

        assertEquals(originalText, decryptor.decrypt(base64(encrypted)))
    }

    @Test
    fun testNoActualEncryption() {
        assertEquals("a", PasswordNotEncrypted.decrypt("a"))
    }

    @Test
    fun testBase64() {
        assertEquals("AAAA", base64(ByteArray(3)))
        assertEquals(ByteArray(3), base64("AAAA"))
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
        fun Byte.h() = Integer.toHexString(toInt() and 0xff)
        assertEquals(a.map { it.h() }, b.map { it.h() })
    }
}