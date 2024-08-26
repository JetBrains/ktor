/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*
import java.util.zip.*
import kotlin.test.*

abstract class CompressionTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @OptIn(InternalAPI::class)
    @Test
    fun testLocalFileContentWithCompression() = runTest {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            install(Compression)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/", { header(HttpHeaders.AcceptEncoding, "gzip") }) {
            assertEquals(200, status.value)
            assertEquals(file.readText(), GZIPInputStream(rawContent.toInputStream()).reader().use { it.readText() })
            assertEquals("gzip", headers[HttpHeaders.ContentEncoding])
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testStreamingContentWithCompression() = runTest {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            install(Compression)
            handle {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("Hello!")
                        }
                    }
                )
            }
        }

        withUrl("/", { header(HttpHeaders.AcceptEncoding, "gzip") }) {
            assertEquals(200, status.value)
            assertEquals("Hello!", GZIPInputStream(rawContent.toInputStream()).reader().use { it.readText() })
            assertEquals("gzip", headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testLocalFileContentRangeWithCompression() = runTest {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            install(Compression)
            install(PartialContent)

            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.AcceptEncoding, "gzip")
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(
                file.reader().use { it.read().toChar().toString() },
                bodyAsText(),
                "It should be no compression if range requested",
            )
        }
    }

    @Test
    fun testCompressionWriteToLarge() = runTest {
        val count = 655350
        fun Appendable.produceText() {
            for (i in 1..count) {
                append("test $i\n".padStart(10, ' '))
            }
        }

        createAndStartServer {
            install(Compression)

            get("/") {
                call.respondTextWriter(contentType = ContentType.Text.Plain) {
                    produceText()
                }
            }
        }

        withUrl("/", { headers.append(HttpHeaders.AcceptEncoding, "gzip") }) {
            // ensure the server is running
            val expected = buildString {
                produceText()
            }
            assertTrue(HttpHeaders.ContentEncoding in headers)
            val array = body<ByteArray>()
            val text = GZIPInputStream(ByteArrayInputStream(array)).readBytes().toString(Charsets.UTF_8)
            assertEquals(expected, text)
        }
    }
}
