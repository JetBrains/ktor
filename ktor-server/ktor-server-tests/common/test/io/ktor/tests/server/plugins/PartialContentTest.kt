/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlin.test.*

class PartialContentTest {

    private val localPath = "plugins/StaticContentTest.kt"
    private val fileEtag = "etag-99"
    private val contentType = "Content-Type: application/octet-stream"
    private val content = "test_string".repeat(100).toByteArray()
    private val lastModifiedTime = getTimeMillis()

    private fun withRangeApplication(
        maxRangeCount: Int? = null,
        test: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        install(ConditionalHeaders)
        install(CachingHeaders)
        install(PartialContent) {
            maxRangeCount?.let { this.maxRangeCount = it }
        }
        install(AutoHeadResponse)
        routing {
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
    fun testCustomMaxRangeCountNotAccepted() = runTest {
        assertFailsWith<IllegalArgumentException> {
            runTestApplication {
                install(PartialContent) {
                    maxRangeCount = 0
                }
            }
        }
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        install(AutoHeadResponse)
        routing {
            suspend fun respond(applicationCall: ApplicationCall) {
                applicationCall.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType = ContentType.Application.OctetStream
                        override val contentLength: Long = content.size.toLong()
                        override fun readFrom(): ByteReadChannel = ByteReadChannel(content)
                    }
                )
            }

            route("1") {
                install(PartialContent)
                get {
                    respond(call)
                }
            }
        }

        client.get("1") {
            header(HttpHeaders.Range, "bytes=1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            val contentType = ContentType.parse(result.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContent(result, content.drop(1).take(2).toByteArray())
        }
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRangeLimited() = withRangeApplication(maxRangeCount = 2) {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,2-2,4-4")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 0-4/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            checkContent(result, content.take(5).toByteArray())
        }
    }

    @Test
    fun testSingleByteRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 0-0/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            val contentType = ContentType.parse(result.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContent(result, content.take(1).toByteArray())
        }
    }

    @Test
    fun testTwoBytesRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            val contentType = ContentType.parse(result.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContent(result, content.drop(1).take(2).toByteArray())
        }
    }

    @Test
    fun testUnsatisfiableTailRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=-0") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.status.value)
            assertEquals("bytes */${content.size}", result.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testUnsatisfiableRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1000000-1000004") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.status.value)
            assertEquals("bytes */${content.size}", result.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testSyntacticallyIncorrectRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
        }
    }

    @Test
    fun testGoodAndBadTailRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 0-0/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            checkContent(result, content.take(1).toByteArray())
        }
    }

    @Test
    fun testGoodAndBadRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 0-0/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            checkContent(result, content.take(1).toByteArray())
        }
    }

    @Test
    fun testHeadRequestRange() = withRangeApplication {
        // head request
        client.head(localPath) {
            header(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertNotNull(result.headers[HttpHeaders.LastModified])
            assertEquals(RangeUnits.Bytes.unitToken, result.headers[HttpHeaders.AcceptRanges])
            assertTrue { result.bodyAsChannel().readRemaining().readByteArray().isEmpty() }
        }
    }

    @Test
    fun testPostRequestRange() = withRangeApplication {
        // post request
        client.post(localPath) {
            header(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(
                HttpStatusCode.MethodNotAllowed.description("Method POST is not allowed with range request"),
                result.status
            )
        }
    }

    @Test
    fun testPostNoRange() = withRangeApplication {
        // post request with no range
        client.post(localPath) {
        }.let { result ->
            assertEquals(RangeUnits.Bytes.unitToken, result.headers[HttpHeaders.AcceptRanges])
            assertEquals(HttpStatusCode.OK, result.status)
        }
    }

    @Test
    fun testBypassContentLength() = withRangeApplication {
        client.get(localPath) {
        }.let { result ->
            assertEquals(content.size, result.headers[HttpHeaders.ContentLength]!!.toInt())
        }
    }

    @Test
    fun testMultipleMergedRanges() = withRangeApplication {
        // multiple ranges should be merged into one
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 0-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            assertNotNull(result.headers[HttpHeaders.LastModified])
            checkContent(result, content.take(3).toByteArray())
        }
    }

    @Test
    fun testDontCrashWithEmptyIfRange() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, "")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            checkContent(result, content.drop(1).take(2).toByteArray())
        }
    }

    @Test
    fun testIfRangeETag() = withRangeApplication {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, "\"$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
        }

        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, "\"wrong-$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals(null, result.headers[HttpHeaders.ContentRange])
            checkContent(result, content)
        }
    }

    @Test
    fun testIfRangeDate() = withRangeApplication {
        val fileDate = GMTDate(lastModifiedTime)

        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, fileDate.toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            checkContent(result, content.drop(1).take(2).toByteArray())
        }

        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, fileDate.plus(10000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.status)
            assertEquals("bytes 1-2/${content.size}", result.headers[HttpHeaders.ContentRange])
            checkContent(result, content.drop(1).take(2).toByteArray())
        }

        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, fileDate.minus(100000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals(null, result.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testIfRangeWrongDate() = withRangeApplication {
        val fileDate = GMTDate(lastModifiedTime)

        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=1-2")
            header(HttpHeaders.IfRange, fileDate.toHttpDate().drop(15))
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals(null, result.headers[HttpHeaders.ContentRange])
        }
    }

    private suspend fun checkContent(
        response: HttpResponse,
        expected: ByteArray
    ) {
        val bytes = response.bodyAsChannel().readRemaining().readByteArray()
        assertContentEquals(expected, bytes)
        assertEquals(expected.size, response.headers[HttpHeaders.ContentLength]!!.toInt())
    }
}
