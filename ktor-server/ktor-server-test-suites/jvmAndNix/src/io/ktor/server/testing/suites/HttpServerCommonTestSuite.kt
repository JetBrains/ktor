/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.test.Test

abstract class HttpServerCommonTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testRedirect() {
        createAndStartServer {
            handle {
                call.respondRedirect(Url("http://localhost:${call.request.port()}/page"), true)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)
        }
    }

    @Test
    fun testRedirectFromInterceptor() {
        createAndStartServer {
            application.intercept(ApplicationCallPipeline.Plugins) {
                call.respondRedirect("/2", true)
            }
        }

        withUrl("/1/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)

            assertEquals("/2", headers[HttpHeaders.Location])
        }
    }

    @Test
    fun testHeader() {
        createAndStartServer {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("test-etag", headers[HttpHeaders.ETag])
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun testAllHeaders() {
        createAndStartServer {
            handle {
                call.response.headers.append("Name-1", "value-1")
                call.response.headers.append("Name-1", "value-2")
                call.response.headers.append("Name-2", "value")
                call.respondText(call.response.headers.allValues().toString())
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            val body = call.response.bodyAsText()
            assertTrue(body.contains("Name-1=[value-1, value-2]"))
            assertTrue(body.contains("Name-2=[value]"))
        }
    }

    @Test
    open fun testHeadRequest() {
        createAndStartServer {
            application.install(AutoHeadResponse)
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/", { method = HttpMethod.Head }) {
            assertEquals(200, status.value)
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun testCookie() {
        createAndStartServer {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", headers[HttpHeaders.SetCookie])
        }
    }

    @Test
    fun testPathComponentsDecoding() {
        createAndStartServer {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }

        withUrl("/a%20b") {
            assertEquals(200, status.value)
            assertEquals("space", bodyAsText())
        }
        withUrl("/a+b") {
            assertEquals(200, status.value)
            assertEquals("plus", bodyAsText())
        }
    }

    @Test
    fun testFormUrlEncoded() {
        createAndStartServer {
            post("/") {
                call.respondText("${call.parameters["urlp"]},${call.receiveParameters()["formp"]}")
            }
        }

        withUrl(
            "/?urlp=1",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("formp=2".toByteArray(), ContentType.Application.FormUrlEncoded))
            }
        ) {
            assertEquals(HttpStatusCode.OK.value, status.value)
            assertEquals("1,2", bodyAsText())
        }
    }

    @Test
    fun testRequestTwiceNoKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "close")
            }
        ) {
            assertEquals("Text", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "close")
            }
        ) {
            assertEquals("Text", bodyAsText())
        }
    }

    @Test
    fun testRequestTwiceWithKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "keep-alive")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Text", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "keep-alive")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Text", bodyAsText())
        }
    }

    @Test
    fun test404() {
        createAndStartServer {
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }

        withUrl("/aaaa") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }
    }

    @Test
    fun testStatusPages404() {
        createAndStartServer {
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, _ ->
                    call.respondText(ContentType.parse("text/html"), HttpStatusCode.NotFound) {
                        "Error string"
                    }
                }
            }
        }

        withUrl("/non-existent") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
            assertEquals("Error string", bodyAsText())
        }
    }

    @Test
    fun testRemoteAddress() {
        createAndStartServer {
            handle {
                call.respondText {
                    call.request.local.remoteAddress
                }
            }
        }

        withUrl("/") {
            bodyAsText().also { text ->
                assertNotNull(
                    listOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "0.0.0.0").find {
                        it == text
                    }
                )
            }
        }
    }

    @Test
    fun testRequestParameters() {
        createAndStartServer {
            get("/*") {
                call.respond(call.request.queryParameters.getAll(call.request.path().removePrefix("/")).toString())
            }
        }

        withUrl("/single?single=value") {
            assertEquals("[value]", bodyAsText())
        }
        withUrl("/multiple?multiple=value1&multiple=value2") {
            assertEquals("[value1, value2]", bodyAsText())
        }
        withUrl("/missing") {
            assertEquals("null", bodyAsText())
        }
    }

    @Test
    fun testStatusCodeDirect() {
        createAndStartServer {
            get("/") {
                call.response.status(HttpStatusCode.Found)
                call.respond("Hello")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    fun testStatusCodeViaResponseObject() {
        var completed = false
        createAndStartServer {
            get("/") {
                call.respond(HttpStatusCode.Found)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            completed = true
        }
        assertTrue(completed)
    }

    @Test
    fun testProxyHeaders() {
        createAndStartServer {
            application.install(XForwardedHeaders)
            get("/") {
                call.respond(call.url { })
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host:90")
            }
        ) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host:90/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host")
            }
        ) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host:90")
                header(HttpHeaders.XForwardedProto, "https")
            }
        ) {
            assertEquals("https://my-host:90/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host")
                header(HttpHeaders.XForwardedProto, "https")
            }
        ) {
            assertEquals("https://my-host/", bodyAsText())
        }
    }

    @Test
    fun testRequestParts() {
        createAndStartServer {
            get("/path/1") {
                call.respond(call.request.path())
            }
            get("/document/1") {
                call.respond(call.request.document())
            }
            get("/queryString/1") {
                call.respond(call.request.queryString())
            }
            get("/uri/1") {
                call.respond(call.request.uri)
            }
        }

        withUrl("/path/1?p=v") {
            assertEquals("/path/1", bodyAsText())
        }
        withUrl("/path/1?") {
            assertEquals("/path/1", bodyAsText())
        }
        withUrl("/path/1") {
            assertEquals("/path/1", bodyAsText())
        }

        withUrl("/document/1?p=v") {
            assertEquals("1", bodyAsText())
        }
        withUrl("/document/1?") {
            assertEquals("1", bodyAsText())
        }
        withUrl("/document/1") {
            assertEquals("1", bodyAsText())
        }

        withUrl("/queryString/1?p=v") {
            assertEquals("p=v", bodyAsText())
        }
        withUrl("/queryString/1?") {
            assertEquals("", bodyAsText())
        }
        withUrl("/queryString/1") {
            assertEquals("", bodyAsText())
        }

        withUrl("/uri/1?p=v") {
            assertEquals("/uri/1?p=v", bodyAsText())
        }
        withUrl("/uri/1?") {
            assertEquals("/uri/1?", bodyAsText())
        }
        withUrl("/uri/1") {
            assertEquals("/uri/1", bodyAsText())
        }
    }

    @OptIn(UseHttp2Push::class)
    @Test
    @Http2Only
    fun testServerPush() {
        createAndStartServer {
            get("/child") {
                call.respondText("child")
            }

            get("/") {
                call.push("/child")
                call.respondText("test")
            }
        }

        withUrl("/child") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("child", bodyAsText())
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("test", bodyAsText())
        }
    }

    @Test
    fun testHeadersReturnCorrectly() {
        createAndStartServer {
            get("/") {
                assertEquals("foo", call.request.headers["X-Single-Value"])
                assertEquals("foo,bar", call.request.headers["X-Double-Value"])

                assertNull(call.request.headers["X-Nonexistent-Header"])
                assertNull(call.request.headers.getAll("X-Nonexistent-Header"))

                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        withUrl(
            "/",
            {
                headers {
                    append("X-Single-Value", "foo")
                    append("X-Double-Value", "foo")
                    append("X-Double-Value", "bar")
                }
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("OK", bodyAsText())
        }
    }

    @Test
    fun testParentContextPropagates() {
        createAndStartServer(
            parent = TestData("parent")
        ) {
            get("/") {
                val valueFromContext = kotlin.coroutines.coroutineContext[TestData]!!.name
                call.respond(HttpStatusCode.OK, valueFromContext)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("parent", bodyAsText())
        }
    }

    @Test
    fun testNoRespond() {
        createAndStartServer {
            get("/") {
                call.response.status(HttpStatusCode.Accepted)
                call.response.header("Custom-Header", "Custom value")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Accepted, status)
            assertEquals(headers["Custom-Header"], "Custom value")
        }
    }

    @Test
    fun queryParameterContainingSemicolon() {
        createAndStartServer {
            handle {
                assertEquals("01;21", call.request.queryParameters["code"])
                assertEquals("01;21", call.request.rawQueryParameters["code"])
                call.respond(HttpStatusCode.OK)
            }
        }

        withUrl(
            "/",
            {
                url {
                    encodedParameters.append("code", "01;21")
                }
            }
        ) {
            assertEquals(200, status.value)
        }
    }

    @Test
    fun testRawAndDecodedQueryParameter() {
        createAndStartServer {
            handle {
                assertEquals("value&1+2 3", call.request.queryParameters["key"])
                assertEquals("value%261%2B2+3", call.request.rawQueryParameters["key"])
                call.respond(HttpStatusCode.OK)
            }
        }

        withUrl(
            "/",
            {
                url { parameters.append("key", "value&1+2 3") }
            }
        ) {
            assertEquals(200, status.value)
        }
    }

    @Test
    open fun testFlushingHeaders() {
        createAndStartServer {
            route("/timed") {
                post {
                    val byteStream = ByteChannel(autoFlush = true)
                    launch(Dispatchers.Unconfined) {
                        byteStream.writePacket(call.receiveChannel().readRemaining())
                        byteStream.close(null)
                    }
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        override val status: HttpStatusCode = HttpStatusCode.OK
                        override val contentType: ContentType = ContentType.Text.Plain
                        override val headers: Headers = Headers.Empty
                        override val contentLength: Long = 5
                        override fun readFrom() = byteStream
                    })
                }
            }
        }

        runBlocking {
            val client = HttpClient()
            val requestBody = ByteChannel(true)

            client.preparePost("http://127.0.0.1:$port/timed") {
                setBody(requestBody)
            }.execute { httpResponse ->
                assertEquals(httpResponse.status, HttpStatusCode.OK)
                assertEquals(httpResponse.contentType(), ContentType.Text.Plain)

                val channel: ByteReadChannel = httpResponse.body()
                assertEquals(0, channel.availableForRead)

                val content = ByteArray(5) { it.toByte() }
                requestBody.writeFully(content)
                requestBody.close(null)

                assertContentEquals(channel.readRemaining().readBytes(), content)
            }
            client.close()
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    open fun testCanModifyRequestHeaders() {
        createAndStartServer {
            install(
                createRouteScopedPlugin("plugin") {
                    onCall { call ->
                        call.request.setHeader("deleted", null)
                        call.request.setHeader("changed", listOf("new-value1", "new-value-2"))
                    }
                }
            )
            route("/") {
                post {
                    assertEquals(listOf("new-value1", "new-value-2"), call.request.headers.getAll("changed"))
                    assertNull(call.request.headers.getAll("deleted"))
                    assertEquals(listOf("old"), call.request.headers.getAll("original"))
                    call.respond(call.receiveText())
                }
            }
        }

        runBlocking {
            HttpClient().use { client ->
                val requestBody = ByteChannel(true)
                requestBody.writeStringUtf8("test")

                val response = client.post("http://127.0.0.1:$port/") {
                    headers.append("original", "old")
                    headers.append("deleted", "old")
                    headers.append("changed", "old")
                    setBody("test")
                }

                assertEquals("test", response.bodyAsText())
            }
        }
    }

    @OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
    @Test
    open fun testCanModifyRequestBody() {
        createAndStartServer {
            install(
                createRouteScopedPlugin("plugin") {
                    onCall { call ->
                        val oldChannel = call.request.receiveChannel()
                        val newChannel = GlobalScope.writer {
                            channel.writeStringUtf8("start")
                            oldChannel.copyTo(channel)
                            channel.writeStringUtf8("finish")
                        }.channel
                        call.request.setReceiveChannel(newChannel)
                    }
                }
            )
            route("/") {
                post {
                    call.respond(call.receiveText())
                }
            }
        }

        runBlocking {
            HttpClient().use { client ->
                val requestBody = ByteChannel(true)
                requestBody.writeStringUtf8("test")
                requestBody.close()

                val response = client.post("http://127.0.0.1:$port/") {
                    setBody(requestBody)
                }

                assertEquals("starttestfinish", response.bodyAsText())
            }
        }
    }

    @Test
    fun testHSTSWithCustomPlugin() {
        createAndStartServer {
            val plugin = createApplicationPlugin("plugin") {
                on(CallSetup) { call ->
                    call.mutableOriginConnectionPoint.scheme = "https"
                    call.mutableOriginConnectionPoint.serverPort = 443
                }

                onCall { call ->
                    call.respondText { "From plugin" }
                }
            }
            check(this is RouteNode)
            application.install(plugin)
            application.install(HSTS)

            get("/") {
                call.respondText { "OK" }
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("From plugin", bodyAsText())
        }
    }

    @Test
    fun testErrorInBodyAndStatusIsSet() {
        var throwError = false
        createAndStartServer {
            val plugin = createApplicationPlugin("plugin") {
                onCallRespond { _ ->
                    throwError = !throwError
                    if (throwError) {
                        throw ExpectedTestException("Test exception")
                    }
                }
            }
            application.install(plugin)

            get("/") {
                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError, status)
            assertTrue(bodyAsText().isEmpty())
        }
    }

    @Test
    fun testErrorInBodyClosesConnection() {
        createAndStartServer {
            get("/") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("first\n")
                            channel.writeStringUtf8("second\n")
                            throw ExpectedTestException("error")
                        }
                    }
                )
            }
        }

        try {
            withUrl("/") {
                body<ByteArray>()
            }
        } catch (cause: Throwable) {
            // expected
        }
    }

    @Test
    open fun testErrorInBodyClosesConnectionWithContentLength() {
        createAndStartServer {
            get("/") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long = 100

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("first\n")
                            channel.writeStringUtf8("second\n")
                            throw ExpectedTestException("error")
                        }
                    }
                )
            }
        }

        try {
            withUrl("/") {
                body<ByteArray>()
            }
        } catch (cause: Throwable) {
            // expected
        }
    }

    private data class TestData(
        val name: String
    ) : AbstractCoroutineContextElement(TestData) {
        /**
         * Key for [CoroutineName] instance in the coroutine context.
         */
        companion object Key : CoroutineContext.Key<TestData>
    }
}
