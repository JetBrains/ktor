/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

expect val testResourcesFolder: String

class TlsClientServerSocketTest {

    //this test show how to configure authentication fully common - will work both on native and JVM with the same configuration
    @Test
    fun testClientServer() = runBlocking {
        SelectorManager(Dispatchers.IOBridge).use { selector ->
            val tcp = aSocket(selector).tcp()

            tcp.bind().use { serverSocket ->
                val serverJob = GlobalScope.launch {
                    while (true) serverSocket.accept()
                        .tls(Dispatchers.Default + CoroutineName("SERVER"), isClient = false) {
                            authentication(privateKeyPassword = { "changeit".toCharArray() }) {
                                pkcs12Certificate("$testResourcesFolder/commonkey.p12") { "changeit".toCharArray() }
                            }
                        }
                        .use { socket ->
                            val reader = socket.openReadChannel()
                            val writer = socket.openWriteChannel()
                            repeat(3) {
                                val line = assertNotNull(reader.readUTF8Line())
                                println("SSS: $line")
                                writer.writeStringUtf8("$line\r\n")
                                writer.flush()
                            }
                            delay(2000) //await reading from client socket
                        }
                }

                tcp.connect(serverSocket.localAddress)
                    .tls(Dispatchers.Default + CoroutineName("CLIENT"), isClient = true) {
                        authentication({ "".toCharArray() }) {} //forces using of SSLEngine
                        //TODO: ser verification here
                    }
                    .use { socket ->
                        socket.openWriteChannel().apply {
                            writeStringUtf8("GET / HTTP/1.1\r\n")
                            writeStringUtf8("Host: www.google.com\r\n")
                            writeStringUtf8("Connection: close\r\n")
                            flush()
                        }
                        val reader = socket.openReadChannel()
                        repeat(3) {
                            println("CCC: ${assertNotNull(reader.readUTF8Line())}")
                        }
                    }
                serverJob.cancelAndJoin()
            }
        }
    }
}
