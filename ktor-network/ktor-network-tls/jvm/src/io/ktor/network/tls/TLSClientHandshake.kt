/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.SecretExchangeType.*
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.cipher.*
import io.ktor.network.tls.extensions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.interfaces.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import javax.net.ssl.*
import javax.security.auth.x500.*
import kotlin.coroutines.*

internal class TLSClientHandshake(
    rawInput: ByteReadChannel,
    rawOutput: ByteWriteChannel,
    private val config: TLSConfig,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    private val digest = Digest()
    private val clientSeed: ByteArray = config.random.generateClientSeed()

    @Volatile
    private lateinit var serverHello: TLSServerHello

    @Volatile
    private lateinit var masterSecret: SecretKeySpec

    private val keyMaterial: ByteArray by lazy {
        with(serverHello.cipherSuite) {
            keyMaterial(
                masterSecret, serverHello.serverSeed + clientSeed,
                keyStrengthInBytes, macStrengthInBytes, fixedIvLength
            )
        }
    }

    private val cipher: TLSCipher by lazy {
        TLSCipher.fromSuite(serverHello.cipherSuite, keyMaterial)
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    val input: ReceiveChannel<TLSRecord> = produce {
        var useCipher = false
        try {
            loop@ while (true) {
                val rawRecord = rawInput.readTLSRecord()
                val record = if (useCipher) cipher.decrypt(rawRecord) else rawRecord
                val packet = record.packet

                when (record.type) {
                    TLSRecordType.Alert -> {
                        val level = TLSAlertLevel.byCode(packet.readByte().toInt())
                        val code = TLSAlertType.byCode(packet.readByte().toInt())

                        if (code == TLSAlertType.CloseNotify) return@produce
                        val cause = TLSException("Received alert during handshake. Level: $level, code: $code")

                        channel.close(cause)
                        return@produce
                    }
                    TLSRecordType.ChangeCipherSpec -> {
                        check(!useCipher)
                        val flag = packet.readByte()
                        if (flag != 1.toByte()) throw TLSException("Expected flag: 1, received $flag in ChangeCipherSpec")
                        useCipher = true
                        continue@loop
                    }
                    else -> {
                    }
                }

                channel.send(TLSRecord(record.type, packet = packet))
            }
        } catch (cause: ClosedReceiveChannelException) {
            channel.close()
        } catch (cause: Throwable) {
            channel.close(cause)
            // Remote server closed connection
        } finally {
            output.close()
        }
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    val output: SendChannel<TLSRecord> = actor {
        var useCipher = false

        for (rawRecord in channel) {
            try {
                val record = if (useCipher) cipher.encrypt(rawRecord) else rawRecord
                if (rawRecord.type == TLSRecordType.ChangeCipherSpec) useCipher = true

                rawOutput.writeRecord(record)
            } catch (cause: Throwable) {
                channel.close(cause)
            }
        }

        rawOutput.close()
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    private val handshakes = produce<TLSHandshake> {
        while (true) {
            val record = input.receive()
            val packet = record.packet

            while (packet.isNotEmpty) {
                val handshake = packet.readTLSHandshake()
                if (handshake.type == TLSHandshakeType.HelloRequest) continue
                if (handshake.type != TLSHandshakeType.Finished) {
                    digest += handshake
                }

                channel.send(handshake)

                if (handshake.type == TLSHandshakeType.Finished) {
                    packet.release()
                    return@produce
                }
            }
        }
    }

    suspend fun negotiate() {
        digest.use {
            sendClientHello()
            serverHello = receiveServerHello()

            verifyHello(serverHello)
            handleCertificatesAndKeys()
            receiveServerFinished()
        }
    }

    private fun verifyHello(serverHello: TLSServerHello) {
        val suite = serverHello.cipherSuite
        check(suite in config.cipherSuites) { "Unsupported cipher suite ${suite.name} in SERVER_HELLO" }

        val clientExchanges = SupportedSignatureAlgorithms.filter {
            it.hash == suite.hash && it.sign == suite.signatureAlgorithm
        }

        if (clientExchanges.isEmpty())
            throw TLSException("No appropriate hash algorithm for suite: $suite")

        val serverExchanges = serverHello.hashAndSignAlgorithms
        if (serverExchanges.isEmpty()) return

        if (!clientExchanges.any { it in serverExchanges }) throw TLSException(
            "No sign algorithms in common. \n" +
                "Server candidates: $serverExchanges \n" +
                "Client candidates: $clientExchanges"
        )
    }

    private suspend fun sendClientHello() {
        sendHandshakeRecord(TLSHandshakeType.ClientHello) {
            // TODO: support session id
            writeTLSClientHello(
                TLSVersion.TLS12, config.cipherSuites, clientSeed, ByteArray(32), config.serverName
            )
        }
    }

    private suspend fun receiveServerHello(): TLSServerHello {
        val handshake = handshakes.receive()

        check(handshake.type == TLSHandshakeType.ServerHello) {
            ("Expected TLS handshake ServerHello but got ${handshake.type}")
        }

        return handshake.packet.readTLSServerHello()
    }

    private suspend fun handleCertificatesAndKeys() {
        val exchangeType = serverHello.cipherSuite.exchangeType
        var serverCertificate: Certificate? = null
        var certificateInfo: CertificateInfo? = null
        var encryptionInfo: EncryptionInfo? = null

        while (true) {
            val handshake = handshakes.receive()
            val packet = handshake.packet

            when (handshake.type) {
                TLSHandshakeType.Certificate -> {
                    val certs = packet.readTLSCertificate()
                    val x509s = certs.filterIsInstance<X509Certificate>()
                    if (x509s.isEmpty()) throw TLSException("Server sent no certificate")

                    val manager = config.trustManager
                    manager.checkServerTrusted(x509s.toTypedArray(), exchangeType.jvmName)

                    serverCertificate = x509s.firstOrNull { certificate ->
                        SupportedSignatureAlgorithms.any {
                            it.name.equals(
                                certificate.sigAlgName,
                                ignoreCase = true
                            )
                        }
                    } ?: throw TLSException("No suitable server certificate received: $certs")
                }
                TLSHandshakeType.CertificateRequest -> {
                    val typeCount = packet.readByte().toInt() and 0xFF
                    val types = packet.readBytes(typeCount)

                    val hashAndSignCount = packet.readShort().toInt() and 0xFFFF
                    val hashAndSign = mutableListOf<HashAndSign>()

                    repeat(hashAndSignCount / 2) {
                        val hash = packet.readByte()
                        val sign = packet.readByte()
                        hashAndSign += HashAndSign(hash, sign)
                    }

                    val authoritiesSize = packet.readShort().toInt() and 0xFFFF
                    val authorities = mutableSetOf<Principal>()

                    var position = 0
                    while (position < authoritiesSize) {
                        val size = packet.readShort().toInt() and 0xFFFF
                        position += size

                        val authority = packet.readBytes(size)
                        authorities += X500Principal(authority)
                    }

                    certificateInfo = CertificateInfo(types, hashAndSign.toTypedArray(), authorities)
                    check(packet.isEmpty)
                }
                TLSHandshakeType.ServerKeyExchange -> {
                    when (exchangeType) {
                        ECDHE -> {
                            val curve = packet.readCurveParams()
                            val point = packet.readECPoint(curve.fieldSize)
                            val hashAndSign = packet.readHashAndSign()

                            val params = buildPacket {
                                // TODO: support other curve types
                                writeByte(ServerKeyExchangeType.NamedCurve.code.toByte())
                                writeShort(curve.code)
                                writeECPoint(point, curve.fieldSize)
                            }

                            val signature = Signature.getInstance(hashAndSign.name)!!.apply {
                                initVerify(serverCertificate)
                                update(buildPacket {
                                    writeFully(clientSeed)
                                    writeFully(serverHello.serverSeed)
                                    writePacket(params)
                                }.readBytes())
                            }

                            val signSize = packet.readShort().toInt() and 0xffff
                            val signedMessage = packet.readBytes(signSize)
                            if (!signature.verify(signedMessage)) throw TLSException("Failed to verify signed message")

                            encryptionInfo = generateECKeys(curve, point)
                        }
                        SecretExchangeType.RSA -> {
                            packet.release()
                            error("Server key exchange handshake doesn't expected in RCA exchange type")
                        }
                    }
                }
                TLSHandshakeType.ServerDone -> {
                    handleServerDone(
                        exchangeType,
                        serverCertificate!!,
                        certificateInfo,
                        encryptionInfo
                    )
                    return
                }
                else -> throw TLSException("Unsupported message type during handshake: ${handshake.type}")
            }
        }
    }

    private suspend fun handleServerDone(
        exchangeType: SecretExchangeType,
        serverCertificate: Certificate,
        certificateInfo: CertificateInfo?,
        encryptionInfo: EncryptionInfo?
    ) {
        val chain = certificateInfo?.let { sendClientCertificate(it) }

        val preSecret: ByteArray = generatePreSecret(encryptionInfo)

        sendClientKeyExchange(
            exchangeType,
            serverCertificate,
            preSecret,
            encryptionInfo
        )

        masterSecret = masterSecret(
            SecretKeySpec(preSecret, serverHello.cipherSuite.hash.macName),
            clientSeed, serverHello.serverSeed
        )
        preSecret.fill(0)

        certificateInfo?.let { sendClientCertificateVerify(it, chain!!) }

        sendChangeCipherSpec()
        sendClientFinished(masterSecret)
    }

    private fun generatePreSecret(encryptionInfo: EncryptionInfo?): ByteArray =
        when (serverHello.cipherSuite.exchangeType) {
            SecretExchangeType.RSA -> ByteArray(48).also {
                config.random.nextBytes(it)
                it[0] = 0x03
                it[1] = 0x03
            }
            SecretExchangeType.ECDHE -> KeyAgreement.getInstance("ECDH")!!.run {
                if (encryptionInfo == null) throw TLSException("ECDHE_ECDSA: Encryption info should be provided")
                init(encryptionInfo.clientPrivate)
                doPhase(encryptionInfo.serverPublic, true)
                generateSecret()!!
            }
        }

    private suspend fun sendClientKeyExchange(
        exchangeType: SecretExchangeType,
        serverCertificate: Certificate,
        preSecret: ByteArray,
        encryptionInfo: EncryptionInfo?
    ) {
        val packet = when (exchangeType) {
            RSA -> buildPacket {
                writeEncryptedPreMasterSecret(preSecret, serverCertificate.publicKey, config.random)
            }
            ECDHE -> buildPacket {
                if (encryptionInfo == null) throw TLSException("ECDHE: Encryption info should be provided")
                writePublicKeyUncompressed(encryptionInfo.clientPublic)
            }
        }

        sendHandshakeRecord(TLSHandshakeType.ClientKeyExchange) { writePacket(packet) }
    }

    private suspend fun sendClientCertificate(info: CertificateInfo): CertificateAndKey? {
        val chainAndKey = config.certificates.find { candidate ->
            val leaf = candidate.certificateChain.first()

            val validAlgorithm = when (leaf.publicKey.algorithm) {
                "RSA" -> info.types.contains(CertificateType.RSA)
                "DSS" -> info.types.contains(CertificateType.DSS)
                else -> false
            }

            if (!validAlgorithm) return@find false

            val hasHashAndSignInCommon = info.hashAndSign.none {
                it.name.equals(leaf.sigAlgName, ignoreCase = true)
            }

            if (hasHashAndSignInCommon) return@find false

            info.authorities.isEmpty() || candidate.certificateChain.any { it.issuerDN in info.authorities }
        }

        sendHandshakeRecord(TLSHandshakeType.Certificate) {
            writeTLSCertificates(chainAndKey?.certificateChain ?: emptyArray())
        }

        return chainAndKey
    }

    private suspend fun sendClientCertificateVerify(info: CertificateInfo, certificateAndKey: CertificateAndKey) {
        val leaf = certificateAndKey.certificateChain.first()
        val hashAndSign = info.hashAndSign.firstOrNull {
            it.name.equals(leaf.sigAlgName, ignoreCase = true)
        } ?: return

        if (hashAndSign.sign == SignatureAlgorithm.DSA) return

        val sign = Signature.getInstance(certificateAndKey.certificateChain.first().sigAlgName)!!
        sign.initSign(certificateAndKey.key)

        sendHandshakeRecord(TLSHandshakeType.CertificateVerify) {
            writeByte(hashAndSign.hash.code)
            writeByte(hashAndSign.sign.code)

            digest.state.preview { sign.update(it.readBytes()) }
            val signBytes = sign.sign()!!

            writeShort(signBytes.size.toShort())
            writeFully(signBytes)
        }
    }

    private suspend fun sendChangeCipherSpec() {
        output.send(TLSRecord(TLSRecordType.ChangeCipherSpec, packet = buildPacket { writeByte(1) }))
    }

    private suspend fun sendClientFinished(masterKey: SecretKeySpec) {
        val checksum = digest.doHash(serverHello.cipherSuite.hash.openSSLName)
        val finished = finished(checksum, masterKey)
        sendHandshakeRecord(TLSHandshakeType.Finished) {
            writePacket(finished)
        }
    }

    private suspend fun receiveServerFinished() {
        val finished = handshakes.receive()

        if (finished.type != TLSHandshakeType.Finished)
            throw TLSException("Finished handshake expected, received: $finished")

        val receivedChecksum = finished.packet.readBytes()
        val expectedChecksum = serverFinished(
            digest.doHash(serverHello.cipherSuite.hash.openSSLName), masterSecret, receivedChecksum.size
        )

        if (!receivedChecksum.contentEquals(expectedChecksum)) {
            throw TLSException(
                """Handshake: ServerFinished verification failed:
                |Expected: ${expectedChecksum.joinToString()}
                |Actual: ${receivedChecksum.joinToString()}
            """.trimMargin()
            )
        }
    }

    private suspend fun sendHandshakeRecord(handshakeType: TLSHandshakeType, block: BytePacketBuilder.() -> Unit) {
        val handshakeBody = buildPacket(block = block)

        val recordBody = buildPacket {
            writeTLSHandshakeType(handshakeType, handshakeBody.remaining.toInt())
            writePacket(handshakeBody)
        }

        digest.update(recordBody)
        val element = TLSRecord(TLSRecordType.Handshake, packet = recordBody)
        output.send(element)
    }
}


private fun SecureRandom.generateClientSeed(): ByteArray {
    val seed = ByteArray(32)
    nextBytes(seed)
    return seed.also {
        val unixTime = (System.currentTimeMillis() / 1000L)
        it[0] = (unixTime shr 24).toByte()
        it[1] = (unixTime shr 16).toByte()
        it[2] = (unixTime shr 8).toByte()
        it[3] = (unixTime shr 0).toByte()
    }
}

private fun generateECKeys(curve: NamedCurve, serverPoint: ECPoint): EncryptionInfo {
    val clientKeys = KeyPairGenerator.getInstance("EC")!!.run {
        initialize(ECGenParameterSpec(curve.name))
        generateKeyPair()!!
    }

    @Suppress("UNCHECKED_CAST")
    val publicKey = clientKeys.public as ECPublicKey
    val factory = KeyFactory.getInstance("EC")!!
    val serverPublic = factory.generatePublic(ECPublicKeySpec(serverPoint, publicKey.params!!))!!

    return EncryptionInfo(serverPublic, clientKeys.public, clientKeys.private)
}
