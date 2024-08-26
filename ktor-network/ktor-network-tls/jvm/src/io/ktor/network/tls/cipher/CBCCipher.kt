/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.cipher

import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*

internal class CBCCipher(
    private val suite: CipherSuite,
    private val keyMaterial: ByteArray
) : TLSCipher {
    private val sendCipher = Cipher.getInstance(suite.jdkCipherName)!!
    private val sendKey: SecretKeySpec = keyMaterial.clientKey(suite)
    private val sendMac = Mac.getInstance(suite.macName)!!

    private val receiveCipher = Cipher.getInstance(suite.jdkCipherName)!!
    private val receiveKey: SecretKeySpec = keyMaterial.serverKey(suite)
    private val receiveMac = Mac.getInstance(suite.macName)!!

    private var inputCounter: Long = 0L
    private var outputCounter: Long = 0L

    override fun encrypt(record: TLSRecord): TLSRecord {
        sendCipher.init(Cipher.ENCRYPT_MODE, sendKey, IvParameterSpec(generateNonce(suite.fixedIvLength)))

        val content = record.packet.readByteArray()
        val macBytes = prepareMac(record, content)

        val encryptionData = buildPacket {
            writeFully(content)
            writeFully(macBytes)
            writePadding()
        }

        val packet = encryptionData.cipherLoop(sendCipher) {
            writeFully(sendCipher.iv)
        }

        return TLSRecord(record.type, packet = packet)
    }

    override fun decrypt(record: TLSRecord): TLSRecord {
        val packet = record.packet
        val serverIV = packet.readByteArray(suite.fixedIvLength)
        receiveCipher.init(Cipher.DECRYPT_MODE, receiveKey, IvParameterSpec(serverIV))

        val content = packet.cipherLoop(receiveCipher).readByteArray()

        val paddingLength = (content[content.size - 1].toInt() and 0xFF)
        val paddingStart = content.size - paddingLength - 1
        val macStart = paddingStart - suite.macStrengthInBytes

        validatePadding(content, paddingStart)
        validateMac(record, content, macStart)

        val decryptedContent = buildPacket {
            writeFully(content, 0, macStart)
        }

        return TLSRecord(record.type, record.version, decryptedContent)
    }

    private fun prepareMac(record: TLSRecord, content: ByteArray): ByteArray {
        sendMac.reset()
        sendMac.init(keyMaterial.clientMacKey(suite))

        val header = ByteArray(13).also {
            it.set(0, outputCounter)
            it[8] = record.type.code.toByte()
            it[9] = 3 // TLS 1.2
            it[10] = 3
            it.set(11, content.size.toShort())
        }

        outputCounter += 1

        sendMac.update(header)
        return sendMac.doFinal(content)
    }

    private fun Sink.writePadding() {
        val lastBlockSize = (size + 1) % sendCipher.blockSize
        val paddingSize: Byte = (sendCipher.blockSize - lastBlockSize).toByte()

        repeat(paddingSize + 1) {
            writeByte(paddingSize)
        }
    }

    private fun validatePadding(content: ByteArray, paddingStart: Int) {
        val padding = content[content.size - 1].toInt() and 0xFF
        for (i in paddingStart until content.size) {
            val byte = content[i].toInt() and 0xFF
            if (padding != byte) throw TLSException("Padding invalid: expected $padding, actual $byte")
        }
    }

    private fun validateMac(record: TLSRecord, content: ByteArray, macOffset: Int) {
        receiveMac.reset()
        receiveMac.init(keyMaterial.serverMacKey(suite))

        val header = ByteArray(13).also {
            it.set(0, inputCounter)
            it[8] = record.type.code.toByte()
            it[9] = 3 // TLS 1.2
            it[10] = 3
            it.set(11, macOffset.toShort())
        }

        inputCounter++

        receiveMac.update(header)
        receiveMac.update(content, 0, macOffset)

        val expectedMac = receiveMac.doFinal()!!
        val actual = content.sliceArray(macOffset until macOffset + suite.macStrengthInBytes)
        if (!MessageDigest.isEqual(expectedMac, actual)) throw TLSException("Failed to verify MAC content")
    }
}
