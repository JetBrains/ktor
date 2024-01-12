/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.*

class ContentEncodingRequestBodyTest {

    @Test
    fun testCompressRequestBody() = testApplication {
        routing {
            post("gzip") {
                assertEquals("gzip", call.request.headers[HttpHeaders.ContentEncoding])
                val request = call.receive<ByteArray>()
                call.respond(GZipEncoder.decode(ByteReadChannel(request)))
            }
            post("deflate") {
                assertEquals("deflate", call.request.headers[HttpHeaders.ContentEncoding])
                val request = call.receive<ByteArray>()
                call.respond(DeflateEncoder.decode(ByteReadChannel(request)))
            }
            post("multiple") {
                assertEquals("deflate,gzip", call.request.headers[HttpHeaders.ContentEncoding])
                val request = call.receive<ByteArray>()
                call.respond(DeflateEncoder.decode(GZipEncoder.decode(ByteReadChannel(request))))
            }
        }

        val body = ByteArray(500) { it.toByte() }
        val client = createClient {
            ContentEncoding(mode = ContentEncodingConfig.Mode.All)
        }

        val gzipResponse = client.post("/gzip") {
            compress(GZipEncoder.name)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, gzipResponse.status)
        assertContentEquals(body, gzipResponse.body<ByteArray>())

        val deflateResponse = client.post("/deflate") {
            compress(DeflateEncoder.name)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, deflateResponse.status)
        assertContentEquals(body, deflateResponse.body<ByteArray>())

        val multipleResponse = client.post("/multiple") {
            compress(DeflateEncoder.name, GZipEncoder.name)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, multipleResponse.status)
        assertContentEquals(body, multipleResponse.body<ByteArray>())

        assertFailsWith<UnsupportedContentEncodingException> {
            client.post("/multiple") {
                compress(DeflateEncoder.name, DeflateEncoder.name, "unknown")
                setBody(body)
            }
        }

        val gzipByteWriteChanelResponse = client.post("/gzip") {
            compress(GZipEncoder.name)
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(body)
                }
            })
        }
        assertEquals(HttpStatusCode.OK, gzipByteWriteChanelResponse.status)
        assertContentEquals(body, gzipByteWriteChanelResponse.body<ByteArray>())
    }
}
