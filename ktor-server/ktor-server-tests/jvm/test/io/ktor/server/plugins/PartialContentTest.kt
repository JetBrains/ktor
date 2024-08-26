/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.io.*
import kotlin.test.*

class PartialContentTest {

    private val localPath = "plugins/StaticContentTest.kt"
    private val fileEtag = "etag-99"
    private val contentType = "Content-Type: application/octet-stream"
    private val content = "test_string".repeat(100).toByteArray()
    private val lastModifiedTime = getTimeMillis()

    private fun withRangeApplication(maxRangeCount: Int? = null, test: TestApplicationEngine.() -> Unit): Unit =
        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            application.install(PartialContent) {
                maxRangeCount?.let { this.maxRangeCount = it }
            }
            application.install(AutoHeadResponse)
            application.routing {
                route(localPath) {
                    handle {
                        val channel = ByteReadChannel(content)
                        call.respond(
                            object : OutgoingContent.ReadChannelContent() {
                                override val contentType: ContentType = ContentType.Application.OctetStream
                                override val contentLength: Long = content.size.toLong()
                                override fun readFrom(): ByteReadChannel = channel
                            }.apply {
                                versions += LastModifiedVersion(GMTDate(lastModifiedTime))
                                versions += EntityTagVersion(fileEtag)
                            }
                        )
                    }
                }
            }

            test()
        }

    @Test
    fun testCustomMaxRangeCountAcceptedRange(): Unit = withRangeApplication(maxRangeCount = 2) {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            checkContentLength(result)
        }
    }

    @Test
    fun testMultipleRanges(): Unit = withRangeApplication {
        // multiple ranges
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { result ->
            checkContentLength(result)
            val lines = result.response.content!!.lines()
            assertTrue(lines[0] == contentType || lines[1] == contentType)

            assertMultipart(result) { parts ->
                assertEquals(listOf("t", "t"), parts)
            }
        }
    }

    private fun assertMultipart(result: TestApplicationCall, block: (List<String>) -> Unit) {
        assertEquals(HttpStatusCode.PartialContent, result.response.status())
        assertNotNull(result.response.headers[HttpHeaders.LastModified])
        val contentType = ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!)
        assertTrue(contentType.match(ContentType.MultiPart.ByteRanges))
        assertNotNull(contentType.parameter("boundary"))

        val parts = result.response.content!!.reader().buffered().parseMultipart(contentType.parameter("boundary")!!)
        assertTrue { parts.isNotEmpty() }

        block(parts)
    }

    private fun BufferedReader.parseMultipart(boundary: String): List<String> {
        val parts = ArrayList<String>()
        do {
            // according to rfc1341
            val prefix = findLineWithBoundary(boundary) ?: fail("Unexpected end")

            assert(prefix.startsWith("--$boundary"))

            if (prefix.endsWith("--$boundary--")) break
            val headers = scanHeaders()

            assertFalse(headers.isEmpty())
            assertNotNull(headers[HttpHeaders.ContentType])

            val range = headers[HttpHeaders.ContentRange]?.contentRange()
                ?: fail("Content-Range is missing in the part")

            val length = range.first.length.toInt()
            require(length > 0) { "range shouldn't be empty" }

            parts.add(
                buildString {
                    repeat(length) {
                        append(read().toChar())
                    }
                }
            )
        } while (true)

        return parts
    }

    private fun BufferedReader.findLineWithBoundary(boundary: String): String? {
        do {
            val line = readLine() ?: return null
            if (line.contains(boundary)) return line
        } while (true)
    }

    private fun BufferedReader.scanHeaders() = Headers.build {
        do {
            val line = readLine()
            if (line.isNullOrBlank()) break

            val (header, value) = line.chomp(":") { throw IOException("Illegal header line $line") }
            append(header.trimEnd(), value.trimStart())
        } while (true)
    }

    private fun String.contentRange(): Pair<LongRange, Long> {
        assertTrue { startsWith("bytes ") }

        val (range, size) = removePrefix("bytes ")
            .trimStart()
            .chomp("/") { throw IOException("Missing slash / in Content-Range header value $this") }

        val (from, to) = range
            .chomp("-") { throw IOException("Missing range delimiter in Content-Range value $this") }

        return (from.toLong()..to.toLong()) to size.toLong()
    }

    private val LongRange.length: Long
        get() = (endInclusive - start + 1).coerceAtLeast(0L)

    private inline fun String.chomp(
        separator: String,
        onMissingDelimiter: () -> Pair<String, String>
    ): Pair<String, String> {
        return when (val idx = indexOf(separator)) {
            -1 -> onMissingDelimiter()
            else -> substring(0, idx) to substring(idx + 1)
        }
    }

    private fun File.readChars(from: Int, toInclusive: Int = from): String {
        require(from <= toInclusive)

        val result = CharArray(toInclusive - from + 1)
        reader().use { input ->
            if (from > 0) {
                assertEquals(from.toLong(), input.skip(from.toLong()))
            }
            assertEquals(result.size, input.read(result))
        }
        return String(result)
    }

    private fun checkContentLength(result: TestApplicationCall) {
        assertEquals(
            result.response.byteContent!!.size,
            result.response.headers[HttpHeaders.ContentLength]!!.toInt()
        )
    }
}
