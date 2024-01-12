/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.test.*
import kotlin.test.Test

abstract class ContentTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testTextContent() {
        createAndStartServer {
            handle {
                call.respondText("test")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)

            val fields = HeadersBuilder()
            fields.appendAll(headers)

            fields.remove(HttpHeaders.Date) // Do not check for Date field since it's unstable

            // Check content type manually because spacing and case can be different per engine
            val contentType = fields.getAll(HttpHeaders.ContentType)?.single()
            fields.remove(HttpHeaders.ContentType)
            assertNotNull(contentType) // Content-Type should be present
            val parsedContentType = ContentType.parse(contentType) // It should parse
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), parsedContentType)

            assertEquals("4", headers[HttpHeaders.ContentLength])
            assertEquals("test", bodyAsText())
        }
    }

    @Test
    fun testStream() {
        createAndStartServer {
            handle {
                call.respondTextWriter {
                    write("ABC")
                    flush()
                    write("123")
                    flush()
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("ABC123", bodyAsText())
        }
    }

    @Test
    fun testBinary() {
        createAndStartServer {
            handle {
                call.respondOutputStream {
                    write(25)
                    write(37)
                    write(42)
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(ContentType.Application.OctetStream, contentType())
            assertTrue(byteArrayOf(25, 37, 42).contentEquals(readBytes()))
        }
    }

    @Test
    fun testBinaryUsingChannel() {
        createAndStartServer {
            handle {
                call.respondBytesWriter {
                    writeByte(25)
                    writeByte(37)
                    writeByte(42)
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(ContentType.Application.OctetStream, contentType())
            assertTrue(byteArrayOf(25, 37, 42).contentEquals(readBytes()))
        }
    }

    @Test
    fun testLocalFileContent() {
        val file = listOf(File("jvm"), File("ktor-server/ktor-server/jvm"))
            .filter { it.exists() }
            .flatMap { it.walkBottomUp().filter { it.extension == "kt" }.asIterable() }
            .first()

        testLog.trace("test file is $file")

        createAndStartServer {
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(file.readText(), bodyAsText(Charsets.UTF_8))
        }
    }

    @Test
    fun testLocalFileContentRange() {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            install(PartialContent)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        val fileContentHead = String(file.reader().use { input -> CharArray(32).also { input.read(it) } })

        withUrl(
            "/",
            {
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(fileContentHead.substring(0, 1), bodyAsText())
        }
        withUrl(
            "/",
            {
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(1, 2))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(fileContentHead.substring(1, 3), bodyAsText())
        }
    }

    @Test
    fun testJarFileContent() {
        createAndStartServer {
            handle {
                call.respond(call.resolveResource("/ArrayList.class", "java.util")!!)
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readBytes().let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testURIContent() {
        createAndStartServer {
            handle {
                call.respond(
                    URIFileContent(
                        this::class.java.classLoader.getResources("java/util/ArrayList.class").toList().first()
                    )
                )
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readBytes().let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testURIContentLocalFile() {
        val file = File("build/classes/").walkBottomUp()
            .filter { it.extension == "class" }.first()
        testLog.trace("test file is $file")

        createAndStartServer {
            handle {
                call.respond(URIFileContent(file.toURI()))
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readBytes().let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testRequestContentFormData() {
        createAndStartServer {
            handle {
                val parameters = runCatching { call.receiveNullable<Parameters>() }.getOrNull()
                if (parameters != null) {
                    call.respond(parameters.formUrlEncode())
                } else {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(TextContent(parametersOf("a", "1").formUrlEncode(), ContentType.Application.FormUrlEncoded))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("a=1", bodyAsText())
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.UnsupportedMediaType.value, status.value)
        }
    }

    @Test
    @NoHttp2
    open fun testChunked() {
        val data = ByteArray(16 * 1024, { it.toByte() })
        val size = data.size.toLong()

        createAndStartServer {
            get("/chunked") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.close()
                        }
                    }
                )
            }
            get("/pseudo-chunked") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long? get() = size
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.close()
                        }
                    }
                )
            }
            get("/array") {
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override val contentLength: Long? get() = size
                        override fun bytes(): ByteArray = data
                    }
                )
            }
            get("/array-chunked") {
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override fun bytes(): ByteArray = data
                    }
                )
            }
            get("/read-channel") {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    }
                )
            }
            get("/fixed-read-channel") {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentLength: Long? get() = size
                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    }
                )
            }
        }

        withUrl("/array") {
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(data.toList(), call.response.readBytes().toList())
        }

        withUrl("/array-chunked") {
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertEquals(data.toList(), call.response.readBytes().toList())
            assertNull(headers[HttpHeaders.ContentLength])
        }

        withUrl("/chunked") {
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertEquals(data.toList(), call.response.readBytes().toList())
            assertNull(headers[HttpHeaders.ContentLength])
        }

        withUrl("/fixed-read-channel") {
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertEquals(data.toList(), call.response.readBytes().toList())
        }

        withUrl("/pseudo-chunked") {
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertEquals(data.toList(), call.response.readBytes().toList())
        }

        withUrl("/read-channel") {
            assertNull(headers[HttpHeaders.ContentLength])
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertEquals(data.toList(), call.response.readBytes().toList())
        }
    }

    @Test
    fun testStreamNoFlush() {
        createAndStartServer {
            handle {
                call.respondTextWriter {
                    write("ABC")
                    write("123")
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("ABC123", bodyAsText())
        }
    }

    @Test
    fun testSendTextWithContentType() {
        createAndStartServer {
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())

            val contentType = ContentType.parse(headers[HttpHeaders.ContentType]!!)
            val pattern = ContentType.Text.Plain
            assertTrue(contentType.match(pattern))
        }
    }

    @Test
    fun testStaticServe() {
        createAndStartServer {
            staticResources("/files/", "io/ktor/server/testing/suites")
        }

        withUrl("/files/${ContentTestSuite::class.simpleName}.class") {
            assertEquals(200, status.value)
            val bytes = readBytes(8192)
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)

            discardRemaining()
        }
        withUrl("/files/${ContentTestSuite::class.simpleName}.class2") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
            discardRemaining()
        }
        withUrl("/wefwefwefw") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
            discardRemaining()
        }
    }

    @Test
    fun testStaticServeFromDir() {
        val targetClasses = File(classesDir)

        val file = targetClasses.walkBottomUp()
            .first { it.extension == "class" && !it.name.contains('$') }

        val location = file.parentFile!!

        testLog.trace("test file is $file")

        createAndStartServer {
            staticFiles("/files", location)
        }

        withUrl("/files/${file.toRelativeString(location).urlPath()}") {
            assertEquals(200, status.value)

            val bytes = readBytes(100)
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }

        withUrl("/files/${file.toRelativeString(location).urlPath()}2") {
            assertEquals(404, status.value)
        }
        withUrl("/wefwefwefw") {
            assertEquals(404, status.value)
        }
    }

    @Test
    fun testRequestBodyAsyncEcho() {
        createAndStartServer {
            route("/echo") {
                handle {
                    val response = call.receiveChannel().toByteArray()
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override fun readFrom() = ByteReadChannel(response)
                        }
                    )
                }
            }
        }

        withUrl(
            "/echo",
            {
                method = HttpMethod.Post
                setBody(
                    WriterContent(
                        {
                            append("POST test\n")
                            append("Another line")
                            flush()
                        },
                        ContentType.Text.Plain
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("POST test\nAnother line", bodyAsText())
        }
    }

    @Test
    fun testEchoBlocking() {
        createAndStartServer {
            post("/") {
                val text = withContext(Dispatchers.IO) { call.receiveStream().bufferedReader().readText() }
                call.response.status(HttpStatusCode.OK)
                call.respond(text)
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("POST content".toByteArray()))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("POST content", bodyAsText())
        }
    }

    @Test
    fun testRequestContentString() {
        createAndStartServer {
            post("/") {
                call.respond(call.receiveText())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody("Hello")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    @NoHttp2
    open fun testMultipartFileUpload() {
        createAndStartServer {
            post("/") {
                val response = StringBuilder()
                @Suppress("DEPRECATION")
                call.receiveMultipart().readAllParts().sortedBy { it.name }.forEach { part ->
                    when (part) {
                        is PartData.FormItem -> response.append("${part.name}=${part.value}\n")
                        is PartData.FileItem -> response.append(
                            "file:${part.name},${part.originalFileName},${part.provider().readRemaining().readText()}\n"
                        )

                        is PartData.BinaryItem -> {}
                        is PartData.BinaryChannelItem -> {}
                    }

                    part.dispose()
                }

                call.respondText(response.toString())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                val contentType = ContentType.MultiPart.FormData
                    .withParameter("boundary", "***bbb***")
                    .withCharset(Charsets.ISO_8859_1)

                setBody(
                    WriterContent(
                        {
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"a story\"\r\n")
                            append("\r\n")
                            append(
                                "Hi user. The snake you gave me for free ate all the birds. " +
                                    "Please take it back ASAP.\r\n"
                            )
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("\r\n")
                            append("File content goes here\r\n")
                            append("--***bbb***--\r\n")
                            flush()
                        },
                        contentType
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals(
                "a story=Hi user. The snake you gave me for free ate all the birds. " +
                    "Please take it back ASAP.\nfile:attachment,original.txt,File content goes here\n",
                bodyAsText()
            )
        }
    }

    @Test
    @NoHttp2
    open fun testMultipartFileUploadLarge() {
        val numberOfLines = 10000

        createAndStartServer {
            post("/") {
                val response = StringBuilder()

                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> response.append("${part.name}=${part.value}\n")
                        is PartData.FileItem -> {
                            @Suppress("DEPRECATION")
                            val lineSequence = part.streamProvider().bufferedReader().lineSequence()
                            response.append("file:${part.name},${part.originalFileName},${lineSequence.count()}\n")
                        }

                        is PartData.BinaryItem -> {
                        }

                        is PartData.BinaryChannelItem -> {}
                    }

                    part.dispose()
                }

                call.respondText(response.toString())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                val contentType = ContentType.MultiPart.FormData
                    .withParameter("boundary", "***bbb***")
                    .withCharset(Charsets.ISO_8859_1)

                setBody(
                    WriterContent(
                        {
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"a story\"\r\n")
                            append("\r\n")
                            append(
                                "Hi user. The snake you gave me for free ate all the birds. " +
                                    "Please take it back ASAP.\r\n"
                            )
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("\r\n")
                            withContext(coroutineContext) {
                                repeat(numberOfLines) {
                                    append("File content goes here\r\n")
                                }
                            }
                            append("--***bbb***--\r\n")
                            flush()
                        },
                        contentType
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals(
                "a story=Hi user. The snake you gave me for free ate all the birds. " +
                    "Please take it back ASAP.\nfile:attachment,original.txt,$numberOfLines\n",
                bodyAsText()
            )
        }
    }

    @Test
    fun testReceiveInputStream() {
        createAndStartServer {
            post("/") {
                call.respond(withContext(Dispatchers.IO) { call.receive<InputStream>().reader().readText() })
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody("Hello")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    fun testRequestContentInputStream() {
        createAndStartServer {
            post("/") {
                call.respond(withContext(Dispatchers.IO) { call.receiveStream().reader().readText() })
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("Hello".toByteArray(), ContentType.Text.Plain))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    companion object {
        const val classesDir: String = "build/classes/"
    }
}
