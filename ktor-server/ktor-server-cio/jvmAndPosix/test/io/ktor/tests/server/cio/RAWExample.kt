/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.concurrent.*

@Volatile
private var cachedDateText: String = GMTDate().toHttpDate()
private val HelloWorld = "Hello, World!".toByteArray()
private val HelloWorldLength = HelloWorld.size.toString()

private val notFound404_11 = RequestResponseBuilder().apply {
    responseLine("HTTP/1.1", 404, "Not Found")
    headerLine("Content-Length", "0")
    emptyLine()
}.build()

/**
 * This is just an example demonstrating how to create CIO low-level http server
 */
@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val settings = HttpServerSettings()

    GlobalScope.launch {
        while (isActive) {
            cachedDateText = GMTDate().toHttpDate()
            delay(1000)
        }
    }

    val server = GlobalScope.httpServer(
        settings,
        handler = { request ->
            try {
                if (request.uri.length == 1 && request.uri[0] == '/' && request.method == HttpMethod.Get) {
                    val response = RequestResponseBuilder()
                    response.responseLine(request.version, 200, "OK")
                    response.headerLine("Date", cachedDateText)
                    response.headerLine("Content-Length", HelloWorldLength)
                    response.headerLine("Content-Type", "text/plain; charset=utf-8")
                    response.emptyLine()

                    response.bytes(HelloWorld)
                    output.writePacket(response.build())
                } else {
                    output.writePacket(notFound404_11.copy())
                }

                output.flushAndClose()
            } finally {
                request.release()
            }
        }
    )

    runBlocking {
        server.rootServerJob.join()
    }
}
