/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.junit.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import org.junit.jupiter.api.extension.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.test.*

@ExtendWith(RetryOnException::class)
abstract class SustainabilityTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testLoggerOnError() {
        val message = "expected, ${Random().nextLong()}"
        val collected = LinkedBlockingQueue<Throwable>()

        val log = object : Logger by LoggerFactory.getLogger("io.ktor.test") {
            override fun error(message: String, exception: Throwable?) {
                if (exception != null) {
                    collected.add(exception)
                }
            }
        }

        createAndStartServer(log) {
            get("/") {
                throw ExpectedException(message)
            }
            get("/respondWrite") {
                call.respondTextWriter {
                    throw ExpectedException(message)
                }
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError.value, status.value)

            while (true) {
                val exception = collected.poll(timeout.inWholeSeconds, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }

        withUrl("/respondWrite") {
            assertEquals(HttpStatusCode.OK.value, status.value)
            while (true) {
                val exception = collected.poll(timeout.inWholeSeconds, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }
    }

    @Test
    fun testIgnorePostContent(): Unit = runBlocking {
        createAndStartServer {
            post("/") {
                call.respondText("OK")
            }
        }

        socket {
            val bodySize = 65536
            val repeatCount = 10
            val body = "X".repeat(bodySize).toByteArray()

            coroutineScope {
                launch(CoroutineName("writer") + testDispatcher) {
                    RequestResponseBuilder().apply {
                        requestLine(HttpMethod.Post, "/", HttpProtocolVersion.HTTP_1_1.toString())
                        headerLine(HttpHeaders.Host, "localhost:$port")
                        headerLine(HttpHeaders.Connection, "keep-alive")
                        headerLine(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                        headerLine(HttpHeaders.ContentLength, body.size.toString())
                        emptyLine()
                    }.build().use { request ->
                        repeat(repeatCount) {
                            getOutputStream().writePacket(request.copy())
                            getOutputStream().write(body)
                            getOutputStream().flush()
                        }
                    }
                }

                launch(CoroutineName("reader") + testDispatcher) {
                    use {
                        val channel = getInputStream().toByteReadChannel(
                            context = testDispatcher,
                            pool = KtorDefaultPool
                        )

                        repeat(repeatCount) { requestNumber ->
                            parseResponse(channel)?.use { response ->
                                assertEquals(200, response.status)
                                val contentLength = response.headers[HttpHeaders.ContentLength].toString().toLong()
                                channel.discardExact(contentLength)
                                response.release()
                            } ?: kotlin.test.fail("No response found for request #$requestNumber")
                        }
                    }
                }
            }
        }
    }

    @Test
    @NoHttp2
    @Ignore
    open fun testChunkedWrongLength() {
        val data = ByteArray(16 * 1024, { it.toByte() })
        val doubleSize = (data.size * 2).toString()
        val halfSize = (data.size / 2).toString()

        createAndStartServer {
            get("/read-less") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, doubleSize)
                                }

                            override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                        }
                    )
                }
            }
            get("/read-more") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, halfSize)
                                }

                            override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                        }
                    )
                }
            }
            get("/write-less") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.WriteChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, doubleSize)
                                }

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeFully(data)
                                channel.close()
                            }
                        }
                    )
                }
            }
            get("/write-more") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.WriteChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, halfSize)
                                }

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeFully(data)
                                channel.close()
                            }
                        }
                    )
                }
            }
        }

        assertFails {
            withUrl("/read-more") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/write-more") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/read-less") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/write-less") {
                call.body<String>()
            }
        }
    }

    @Test
    fun testApplicationScopeCancellation() {
        var job: Job? = null

        createAndStartServer {
            job = application.launch {
                delay(10000000L)
            }
        }

        server!!.stop(1, 10, TimeUnit.SECONDS)
        assertNotNull(job)
        assertTrue(job!!.isCancelled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testEmbeddedServerCancellation() {
        val parent = Job()

        createAndStartServer(parent = parent) {
            get("/") { call.respondText("OK") }
        }

        withUrl("/") {
            // ensure the server is running
            assertEquals("OK", call.body<String>())
        }

        parent.cancel()

        runBlocking {
            try {
                withTimeout(15000L) {
                    parent.join()
                }
            } catch (cause: TimeoutCancellationException) {
                DebugProbes.printJob(parent)
                fail("Server did shut down in time after cancelling parent!")
            }
        }

        assertFailsWith<IOException> {
            // ensure that the server is not running anymore
            withUrl("/") {
                call.body<String>()
                fail("Shouldn't happen")
            }
        }
    }

    @Test
    fun testGetWithBody() {
        createAndStartServer {
            install(Compression)

            get("/") {
                call.respondText(call.receive())
            }
        }

        val text = "text body"

        withUrl("/", { setBody(text) }) {
            val actual = bodyAsText()
            assertEquals(text, actual)
        }
    }

    @Test
    fun testRepeatRequest() {
        createAndStartServer {
            get("/") {
                call.respond("OK ${call.request.queryParameters["i"]}")
            }
        }

        for (i in 1..100) {
            withUrl("/?i=$i") {
                assertEquals(200, status.value)
                assertEquals("OK $i", bodyAsText())
            }
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    open fun testBlockingConcurrency() {
        val completed = AtomicInteger(0)
        createAndStartServer {
            get("/{index}") {
                val index = call.parameters["index"]!!.toInt()
                call.respondTextWriter {
                    try {
                        append("OK:$index\n")
                    } finally {
                        completed.incrementAndGet()
                    }
                }
            }
        }

        val count = 100
        val latch = CountDownLatch(count)
        val errors = CopyOnWriteArrayList<Throwable>()

        val random = Random()
        for (i in 1..count) {
            thread {
                try {
                    withUrl("/$i") {
                        content.toInputStream().reader().use { reader ->
                            val firstByte = reader.read()
                            if (firstByte == -1) {
                                fail("Premature end of response stream at iteration $i")
                            } else {
                                assertEquals('O', firstByte.toChar())
                                Thread.sleep(random.nextInt(1000).toLong())
                                assertEquals("K:$i\n", reader.readText())
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        if (errors.isNotEmpty()) {
            throw RuntimeException(
                "Exceptions thrown: ${errors.joinToString { it::class.simpleName ?: "<no name>" }}",
                errors.first()
            )
        }
        var multiplier = 1
        if (enableHttp2) multiplier++
        if (enableSsl) multiplier++

        assertEquals(count * multiplier, completed.get())
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testBigFile() {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                for (line in 1..9000000) {
                    for (col in 1..(30 + rnd.nextInt(40))) {
                        out.append('a' + rnd.nextInt(25))
                    }
                    out.append('\n')
                }
            }
        }

        val originalSha1WithSize = file.inputStream().use { it.crcWithSize() }

        createAndStartServer {
            get("/file") {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/file") {
            assertEquals(originalSha1WithSize, content.toInputStream().crcWithSize())
        }
    }

    @Test
    fun testBigFileHttpUrlConnection() {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                for (line in 1..9000000) {
                    for (col in 1..(30 + rnd.nextInt(40))) {
                        out.append('a' + rnd.nextInt(25))
                    }
                    out.append('\n')
                }
            }
        }

        val originalSha1WithSize = file.inputStream().use { it.crcWithSize() }

        createAndStartServer {
            get("/file") {
                call.respond(LocalFileContent(file))
            }
        }

        val connection = URL("http://localhost:$port/file").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        try {
            assertEquals(originalSha1WithSize, connection.inputStream.crcWithSize())
        } finally {
            connection.disconnect()
        }
    }

    @Test
    open fun testBlockingDeadlock() {
        createAndStartServer {
            get("/") {
                call.respondTextWriter(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1)) {
                    TimeUnit.SECONDS.sleep(1)
                    this.write("Deadlock ?")
                }
            }
        }

        val e = Executors.newCachedThreadPool()
        try {
            val q = LinkedBlockingQueue<String>()

            val conns = (0..callGroupSize * 10).map {
                e.submit(
                    Callable<String> {
                        try {
                            URL("http://localhost:$port/").openConnection()
                                .inputStream.bufferedReader().readLine().apply {} ?: "<empty>"
                        } catch (t: Throwable) {
                            "error: ${t.message}"
                        }.apply {
                            q.add(this)
                        }
                    }
                )
            }

            TimeUnit.SECONDS.sleep(5)
            var attempts = 7

            fun dump() {
//            val (valid, invalid) = conns.filter { it.isDone }.partition { it.get() == "Deadlock ?" }
//
//            println("Completed: ${valid.size} valid, ${invalid.size} invalid of ${conns.size} total [attempts $attempts]")
            }

            while (true) {
                dump()

                if (conns.all { it.isDone }) {
                    break
                } else if (q.poll(5, TimeUnit.SECONDS) == null) {
                    if (attempts <= 0) {
                        break
                    }
                    attempts--
                } else {
                    attempts = 7
                }
            }

            dump()

            /* use for debugging */
//        if (conns.any { !it.isDone }) {
//             TimeUnit.SECONDS.sleep(500)
//        }

            assertTrue(conns.all { it.isDone })
        } finally {
            e.shutdownNow()
        }
        TimeUnit.SECONDS.sleep(10)
    }

    @Test
    open fun testChunkedWithVSpace() {
        createAndStartServer {
            post("/") {
                call.receiveParameters()
                call.respond(HttpStatusCode.BadRequest, "")
            }
        }

        val messages = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Content-Length: 1\r\n",
            "Transfer-Encoding:\u000bchunked\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n"
        )

        socket {
            getOutputStream().writer().also { writer ->
                messages.forEach { writer.write(it) }
                writer.flush()
            }

            val result = getInputStream().reader().readLines().joinToString("\n")
            val expected = listOf(
                "HTTP/1.1 400",
                "HTTP/1.0 400"
            )

            assert(expected.any { result.startsWith(it) }) {
                "Invalid response: $result"
            }
        }
    }

    @Test
    fun testChunkedIsNotFinal() {
        createAndStartServer {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
            post("/") {
                call.receiveParameters()
                kotlin.test.fail("We should NOT receive any content")
            }
        }

        val messages = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Length: 1\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Transfer-Encoding: chunked, smuggle\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n"
        )

        socket {
            getOutputStream().writer().also { writer ->
                messages.forEach { writer.write(it) }
                writer.flush()
            }

            val result = getInputStream().reader().readLines().joinToString("\n")
            val expected = listOf(
                "HTTP/1.1 501",
                "HTTP/1.1 400",
                "HTTP/1.0 400"
            )

            assert(expected.any { result.startsWith(it) }) {
                "Invalid response: $result"
            }
        }
    }

    @Test
    @NoHttp2
    fun testHeaderIsTooLong() {
        createAndStartServer {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Plain)
            }
        }

        socket {
            // we use socket instead of http client to ensure we really send too big header
            // since a client may crash itself or do something unsuitable for the test here

            launch(Dispatchers.IO) {
                getOutputStream().writer().apply {
                    write("GET / HTTP/1.1\r\n")
                    write("Host: localhost:$port\r\n")
                    write("MyLongHeader: ")
                    repeat(10000) {
                        write("Abc")
                    }
                    write("\r\n\r\n")
                }
            }

            getInputStream().bufferedReader().apply {
                // we are expecting either 400 BadRequest or 200 OK Hello, World
                val status = readLine().trim().split(" ")[1].toInt()
                assert(status in listOf(200, 400, 431)) {
                    "status should be either 200 or 400 or 431 but it's $status"
                }

                val contentLength = parseHeadersAndGetContentLength()

                if (contentLength == -1) {
                    assertEquals(-1, read())
                } else {
                    skipHttpResponseContent(contentLength)
                }
            }
        }
    }

    class CustomFail(message: String) : Throwable(message)

    @Test
    public fun testErrorInApplicationCallPipelineInterceptor() {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("io.ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationCallPipeline(environment = createTestEnvironment()).items
            .filter { it != ApplicationCallPipeline.ApplicationPhase.Fallback } // fallback will reply with 404 and not 500
            .forEach { phase ->
                val server = createServer(log = logger) {
                    intercept(phase) {
                        throw CustomFail("Failed in phase $phase")
                    }

                    routing {
                        get("/") {
                            call.respond("SUCCESS")
                        }
                    }
                }
                startServer(server)

                withUrl("/") {
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    public fun testErrorInApplicationReceivePipelineInterceptor() {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("io.ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationReceivePipeline().items
            .forEach { phase ->
                val server = createServer(log = logger) {
                    intercept(ApplicationCallPipeline.Setup) {
                        call.request.pipeline.intercept(phase) { throw IllegalStateException("Failed in phase $phase") }
                    }

                    routing {
                        post("/") {
                            val body = call.receive<String>()
                            call.respond("SUCCESS $body")
                        }
                    }
                }
                startServer(server)

                withUrl(
                    "/",
                    { method = HttpMethod.Post; setBody("body") }
                ) {
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    public fun testErrorInApplicationSendPipelineInterceptor() {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationSendPipeline().items
            .filter { it != ApplicationSendPipeline.Engine }
            .forEach { phase ->
                var intercepted = false
                val server = createServer(log = logger) {
                    intercept(ApplicationCallPipeline.Setup) setup@{
                        call.response.pipeline.intercept(phase) {
                            if (intercepted) return@intercept
                            intercepted = true
                            throw IllegalStateException("Failed in phase $phase")
                        }
                    }

                    routing {
                        get("/") {
                            call.respond("SUCCESS")
                        }
                    }
                }
                startServer(server)

                withUrl("/", { intercepted = false }) {
                    body<String>()
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    public open fun testErrorInEnginePipelineInterceptor() {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                println(cause.toString())
                exceptions.add(cause!!)
            }
        }
        val phase = EnginePipeline.Before
        val server = createServer(log = logger) {
            routing {
                get("/req") {
                    call.respond("SUCCESS")
                }
            }
        }
        (server.engine as BaseApplicationEngine).pipeline.intercept(phase) {
            throw IllegalStateException("Failed in engine pipeline")
        }
        startServer(server)

        withUrl("/req") {
            assertEquals(HttpStatusCode.InternalServerError, status, "Failed in engine pipeline")
            assertEquals(exceptions.size, 1, "Failed in phase $phase")
            assertEquals("Failed in engine pipeline", exceptions[0].message)
            exceptions.clear()
        }

        server.stop(1000, 5000, TimeUnit.MILLISECONDS)
    }

    @OptIn(InternalAPI::class)
    @Test
    public fun testRespondBlockingLarge() {
        val server = createServer {
            routing {
                get("/blocking/large") {
                    call.respondTextWriter(ContentType.Text.Plain, HttpStatusCode.OK) {
                        repeat(10000) {
                            write("large string ")
                        }
                    }
                }
            }
        }
        startServer(server)

        withUrl("/blocking/large") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.Plain, contentType()?.withoutParameters())
            val result = content.toInputStream().crcWithSize()
            assertEquals(10000 * 13L, result.second)
        }
    }

    @Test
    fun testDoubleHost() {
        createAndStartServer {
            get("/") {
                call.respond("OK")
            }
        }

        socket {
            val content = """
                GET / HTTP/1.1
                Host: www.example.com
                Host: www.example2.com


            """.trimIndent()

            outputStream.bufferedWriter().apply {
                write(content)
                flush()
            }

            val response = inputStream.bufferedReader()
            val status = response.readLine()

            assertTrue(status.startsWith("HTTP/1.1 400"))
            outputStream.close()
        }
    }

    @Test
    fun testBodySmallerThanContentLength() {
        var failCause: Throwable? = null
        val result = Job()

        createAndStartServer {
            install(RequestValidation) {
                validateContentLength()
            }

            post("/") {
                try {
                    call.receive<ByteArray>().size
                } catch (cause: Throwable) {
                    failCause = cause
                } finally {
                    result.complete()
                }

                call.respond("OK")
            }
        }

        socket {
            val request = buildString {
                append("POST / HTTP/1.1\r\n")
                append("Content-Length: 4\r\n")
                append("Content-Type: text/plain\r\n")
                append("Connection: close\r\n")
                append("Host: localhost\r\n")
                append("\r\n")
                append("ABC")
            }

            outputStream.writer().use {
                it.write(request)
            }
        }

        runBlocking {
            result.join()
        }

        assertTrue(failCause != null)
        assertIs<IOException>(failCause)
    }
}

internal inline fun assertFails(block: () -> Unit) {
    assertFailsWith<Throwable>(block)
}
