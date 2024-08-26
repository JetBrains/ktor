/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.bomremover

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * BOMRemover plugin that allows you to remove Byte Order Mark from response body.
 * You can learn more about [BOM](https://en.wikipedia.org/wiki/Byte_order_mark).
 *
 * Please note that the `Content-Length` header will contain the length of initial response.
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(BOMRemover)
 * }
 * ```
 */
public val BOMRemover: ClientPlugin<Unit> = createClientPlugin(
    name = "BOMRemover",
) {
    on(BOMRemoverHook) { body, context ->
        val beginning = ByteArray(MAX_BOM_SIZE)
        var length = 0
        for (i in beginning.indices) {
            try {
                beginning[i] = body.readByte()
                length++
            } catch (cause: Exception) {
                break
            }
        }

        var offset = 0
        GlobalScope.writer {
            for (bom in BOMs) {
                if (length >= bom.size && bom.indices.all { beginning[it] == bom[it] }) {
                    offset = bom.size
                    break
                }
            }
            channel.writeFully(beginning, startIndex = offset, endIndex = length)

            body.copyTo(channel)
        }.channel
    }
}

private object BOMRemoverHook : ClientHook<suspend (ByteReadChannel, HttpClientCall) -> ByteReadChannel> {
    override fun install(client: HttpClient, handler: suspend (ByteReadChannel, HttpClientCall) -> ByteReadChannel) {
        client.responsePipeline.intercept(HttpResponsePipeline.Receive) { (expectedType, body) ->
            if (body !is ByteReadChannel) return@intercept

            proceedWith(HttpResponseContainer(expectedType, handler(body, context)))
        }
    }
}

private val BOMs = listOf(
    byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte()), // utf-32 (BE)
    byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte()), // utf-32 (LE)
    byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), // utf-8
    byteArrayOf(0xFE.toByte(), 0xFF.toByte()), // utf-16 (BE)
    byteArrayOf(0xFF.toByte(), 0xFE.toByte()) // utf-16 (LE)
)

private val MAX_BOM_SIZE = BOMs.maxOf { it.size }
