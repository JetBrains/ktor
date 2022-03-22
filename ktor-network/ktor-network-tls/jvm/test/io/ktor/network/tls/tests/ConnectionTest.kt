// ktlint-disable experimental:argument-list-wrapping
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.tls.*
import io.ktor.network.tls.SslContext
import io.ktor.network.tls.certificates.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import org.junit.Ignore
import org.junit.Test
import java.io.*
import java.net.ServerSocket
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.io.use
import kotlin.test.*
import kotlin.text.toCharArray

@Suppress("UNCHECKED_CAST")
class ConnectionTest {

    @get:Rule
    val timeout = CoroutinesTimeout.seconds(20)

    @Test
    fun tlsWithoutCloseTest(): Unit = runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager)
            .tcp()
            .connect("www.google.com", port = 443)
            .tls(Dispatchers.Default)
        val channel = socket.openWriteChannel()

        channel.apply {
            writeStringUtf8("GET / HTTP/1.1\r\n")
            writeStringUtf8("Host: www.google.com\r\n")
            writeStringUtf8("Connection: close\r\n\r\n")
            flush()
        }

        socket.openReadChannel().readRemaining()
        Unit
    }

    @OptIn(InternalAPI::class)
    @Test
    fun sslWithoutCloseTest(): Unit = runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager)
            .tcp()
            .connect("www.google.com", port = 443)
            .ssl(Dispatchers.Default, SslContext().createClientEngine())

        val channel = socket.openWriteChannel()
        channel.apply {
            writeStringUtf8("GET / HTTP/1.1\r\n")
            writeStringUtf8("Host: www.google.com\r\n")
            writeStringUtf8("Connection: close\r\n\r\n")
            flush()
        }
        println(socket.openReadChannel().readRemaining().readText())
    }

    @OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
    @Test
    fun sslClientServerTest(): Unit = runBlocking {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withRSA", keySizeInBits = 4096)
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "changeit".toCharArray())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val context = SslContext(SSLContext.getInstance("TLS").also {
            it.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        })

        ActorSelectorManager(Dispatchers.IO).use { selector ->
            val tcp = aSocket(selector).tcp()
            tcp.bind().use { serverSocket ->
                val serverJob = GlobalScope.launch {
                    while (true) serverSocket.accept()
                        .ssl(Dispatchers.IO + CoroutineName("SERVER"), context.createServerEngine()).use { socket ->
                            val reader = socket.openReadChannel()
                            val writer = socket.openWriteChannel()
                            repeat(3) {
                                val line = assertNotNull(reader.readUTF8Line())
                                writer.writeStringUtf8("$line\r\n")
                                writer.flush()
                            }
                            delay(2000) //await reading from client socket
                        }
                }

                tcp.connect(serverSocket.localAddress)
                    .ssl(Dispatchers.IO + CoroutineName("CLIENT"), context.createClientEngine()).use { socket ->
                        socket.openWriteChannel().apply {
                            writeStringUtf8("GET / HTTP/1.1\r\n")
                            writeStringUtf8("Host: www.google.com\r\n")
                            writeStringUtf8("Connection: close\r\n")
                            flush()
                        }
                        val reader = socket.openReadChannel()
                        assertNotNull(reader.readUTF8Line())
                        assertNotNull(reader.readUTF8Line())
                        assertNotNull(reader.readUTF8Line())
                    }
                serverJob.cancelAndJoin()
            }
        }
    }

    @Test
    @Ignore
    fun clientCertificatesAuthTest() {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withRSA", keySizeInBits = 4096)
        val certsChain = keyStore.getCertificateChain("mykey").toList() as List<X509Certificate>
        val certs = certsChain.toTypedArray()
        val password = "changeit".toCharArray()
        val privateKey = keyStore.getKey("mykey", password) as PrivateKey
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(keyStore) }
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        val port = firstFreePort()
        try {
            ServerBootstrap()
                .group(workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val sslContext = SslContextBuilder.forServer(privateKey, *certs)
                                .trustManager(trustManagerFactory)
                                .build()
                            val sslEngine = sslContext.newEngine(ch.alloc()).apply {
                                useClientMode = false
                                needClientAuth = true
                            }
                            ch.pipeline().addLast(SslHandler(sslEngine))
                        }
                    }
                )
                .bind(port)
                .sync()

            tryToConnect(port, trustManagerFactory, keyStore to password)

            try {
                tryToConnect(port, trustManagerFactory)
                fail("TLSException was expected because client has no certificate to authenticate")
            } catch (expected: TLSException) {
            }
        } finally {
            workerGroup.shutdownGracefully()
        }
    }

    private fun tryToConnect(
        port: Int,
        trustManagerFactory: TrustManagerFactory,
        keyStoreAndPassword: Pair<KeyStore, CharArray>? = null
    ) {
        runBlocking {
            aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
                .connect(InetSocketAddress("127.0.0.1", port))
                .tls(Dispatchers.IO) {
                    keyStoreAndPassword?.let { addKeyStore(it.first, it.second) }
                    trustManager = trustManagerFactory
                        .trustManagers
                        .filterIsInstance<X509TrustManager>()
                        .first()
                }
        }.use {
            it.openWriteChannel(autoFlush = true).use { close() }
        }
    }

    private fun firstFreePort(): Int {
        while (true) {
            try {
                val socket = ServerSocket(0, 1)
                val port = socket.localPort
                socket.close()
                return port
            } catch (ignore: IOException) {
            }
        }
    }
}
