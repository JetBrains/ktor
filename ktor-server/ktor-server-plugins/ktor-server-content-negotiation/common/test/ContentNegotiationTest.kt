/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class ContentNegotiationTest {

    private val alwaysFailingConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            fail("This converter should be never started for send")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            fail("This converter should be never started for receive")
        }
    }

    @Test
    fun testRespondByteArray() = testApplication {
        application {
            routing {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, alwaysFailingConverter)
                }
                get("/") {
                    call.respond("test".toByteArray())
                }
            }
        }
        val response = client.get("/").body<ByteArray>()
        assertContentEquals("test".toByteArray(), response)
    }

    object OK

    @Test
    fun testMultipleConverters() = testApplication {
        var nullSerialized = false
        var nullDeserialized = false
        var okSerialized = false
        var okDeserialized = false

        application {
            routing {
                install(ContentNegotiation) {
                    val nullConverter = object : ContentConverter {
                        override suspend fun serialize(
                            contentType: ContentType,
                            charset: Charset,
                            typeInfo: TypeInfo,
                            value: Any?
                        ): OutgoingContent? {
                            nullSerialized = true
                            return null
                        }

                        override suspend fun deserialize(
                            charset: Charset,
                            typeInfo: TypeInfo,
                            content: ByteReadChannel
                        ): Any? {
                            nullDeserialized = true
                            return null
                        }
                    }
                    val okConverter = object : ContentConverter {
                        override suspend fun serialize(
                            contentType: ContentType,
                            charset: Charset,
                            typeInfo: TypeInfo,
                            value: Any?
                        ): OutgoingContent {
                            okSerialized = true
                            return TextContent("OK", contentType)
                        }

                        override suspend fun deserialize(
                            charset: Charset,
                            typeInfo: TypeInfo,
                            content: ByteReadChannel
                        ): Any {
                            okDeserialized = true
                            return OK
                        }
                    }

                    register(ContentType.Application.Json, nullConverter)
                    register(ContentType.Application.Json, okConverter)
                }

                get("/FOO") {
                    try {
                        call.receive<OK>()
                        call.respond(OK)
                    } catch (cause: Throwable) {
                        call.respond(cause)
                    }
                }
            }
        }

        val response = client.get("FOO") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }

        assertEquals("OK", response.bodyAsText())

        assertTrue(nullSerialized)
        assertTrue(nullDeserialized)
        assertTrue(okSerialized)
        assertTrue(okDeserialized)
    }

    private val customContentType = ContentType.parse("application/ktor")

    private val customContentConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is Wrapper) return null
            return TextContent("[${value.value}]", contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != Wrapper::class) return null
            return Wrapper(content.readRemaining().readText().removeSurrounding("[", "]"))
        }
    }

    private val textContentConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is Wrapper) return null
            return TextContent(value.value, contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != Wrapper::class) return null
            return Wrapper(content.readRemaining().readText())
        }
    }

    @Test
    fun testEmpty() = testApplication {
        install(ContentNegotiation) {
        }

        routing {
            get("/") {
                call.respond("OK")
            }
            post("/") {
                val text = call.receive<String>()
                call.respond("OK: $text")
            }
        }

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals("OK", response.bodyAsText())
        }

        client.post("/") {
            setBody("The Text")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals("OK: The Text", response.bodyAsText())
        }
    }

    data class Wrapper(val value: String)

    @Test
    fun testTransformWithNotAcceptable() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Zip, customContentConverter)
        }

        routing {
            post("/") {
                call.respond(Wrapper("hello"))
            }
        }
        client.post("/") {
            setBody(""" {"value" : "value" }""")
            header(HttpHeaders.Accept, "application/xml")
            header(HttpHeaders.ContentType, "application/json")
        }.let { response ->
            assertEquals(HttpStatusCode.NotAcceptable, response.status)
        }
    }

    @Test
    fun testTransformWithUnsupportedMediaType() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Xml, customContentConverter)
        }

        routing {
            post("/") {
                val wrapper = call.receive<Wrapper>()
                call.respond(wrapper.value)
            }
        }
        client.post("/") {
            setBody(""" {"value" : "value" }""")
            header(HttpHeaders.Accept, "application/xml")
            header(HttpHeaders.ContentType, "application/json")
        }.let { response ->
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun testCustom() = testApplication {
        install(ContentNegotiation) {
            register(customContentType, customContentConverter)
        }

        routing {
            get("/") {
                call.respond(Wrapper("OK"))
            }
            post("/") {
                val text = call.receive<Wrapper>().value
                call.respond(Wrapper("OK: $text"))
            }
            post("/raw") {
                val text = call.receiveText()
                call.respond("RAW: $text")
            }
        }

        // Acceptable
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Acceptable with charset
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
            header(HttpHeaders.AcceptCharset, Charsets.ISO_8859_1.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.ISO_8859_1, response.contentType()?.charset())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Acceptable with any charset
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
            header(HttpHeaders.AcceptCharset, "*, ISO-8859-1;q=0.5")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.UTF_8, response.contentType()?.charset())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Acceptable with multiple charsets and one preferred
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
            header(HttpHeaders.AcceptCharset, "ISO-8859-1;q=0.5, UTF-8;q=0.8")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.UTF_8, response.contentType()?.charset())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Missing acceptable charset
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.UTF_8, response.contentType()?.charset()) // should be default
            assertEquals("[OK]", response.bodyAsText())
        }

        // Unacceptable
        client.get("/") {
            header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.NotAcceptable, response.status)
            assertNull(response.headers[HttpHeaders.ContentType])
            assertEquals("", response.bodyAsText())
        }

        // Content-Type pattern
        client.get("/") {
            header(HttpHeaders.Accept, ContentType(customContentType.contentType, "*").toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.UTF_8, response.contentType()?.charset())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Content-Type twice
        client.get("/") {
            header(HttpHeaders.Accept, "$customContentType,$customContentType")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals(Charsets.UTF_8, response.contentType()?.charset())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Post
        client.post("/") {
            header(HttpHeaders.ContentType, customContentType.toString())
            header(HttpHeaders.Accept, customContentType.toString())
            setBody("[The Text]")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK: The Text]", response.bodyAsText())
        }

        // Post to raw endpoint with custom content type
        client.post("/raw") {
            header(HttpHeaders.ContentType, customContentType.toString())
            header(HttpHeaders.Accept, customContentType.toString())
            setBody("[The Text]")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals("RAW: [The Text]", response.bodyAsText())
        }

        // Post with charset
        client.post("/") {
            header(HttpHeaders.ContentType, customContentType.withCharset(Charsets.UTF_8).toString())
            header(HttpHeaders.Accept, customContentType.toString())
            setBody("[The Text]")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK: The Text]", response.bodyAsText())
        }
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        routing {
            route("1") {
                install(ContentNegotiation) {
                    register(customContentType, customContentConverter)
                }
                get { call.respond(Wrapper("OK")) }
            }
            get("2") { call.respond(Wrapper("OK")) }
        }

        client.get("/1") {
            header(HttpHeaders.Accept, customContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK]", response.bodyAsText())
        }

        client.get("/2") {
            header(HttpHeaders.Accept, customContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.NotAcceptable, response.status)
        }
    }

    @Test
    fun testMultiple() = testApplication {
        val textContentConverter: ContentConverter = textContentConverter

        install(ContentNegotiation) {
            // Order here matters. The first registered content type matching the Accept header will be chosen.
            register(customContentType, customContentConverter)
            register(ContentType.Text.Plain, textContentConverter)
        }

        routing {
            get("/") {
                call.respond(Wrapper("OK"))
            }
        }

        // Accept: application/ktor
        client.get("/") {
            header(HttpHeaders.Accept, customContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK]", response.bodyAsText())
        }

        // Accept: text/plain
        client.get("/") {
            header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals("OK", response.bodyAsText())
        }

        // Accept: text/*
        client.get("/") {
            header(HttpHeaders.Accept, ContentType.Text.Any.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals("OK", response.bodyAsText())
        }

        // Accept: */*
        client.get("/") {
            header(HttpHeaders.Accept, ContentType.Any.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK]", response.bodyAsText())
        }

        // No Accept header
        client.get("/") {
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(customContentType, response.contentType()?.withoutParameters())
            assertEquals("[OK]", response.bodyAsText())
        }
    }

    @Suppress("ReplaceSingleLineLet", "MoveLambdaOutsideParentheses")
    @Test
    fun testReceiveTransformedByDefault() = testApplication {
        install(ContentNegotiation) {
            // Order here matters. The first registered content type matching the Accept header will be chosen.
            register(ContentType.Any, alwaysFailingConverter(true))
            ignoreType<String>()
        }

        routing {
            post("/byte-channel") {
                val count = call.receive<ByteReadChannel>().discard()
                call.respondText("bytes: $count")
            }
            post("/byte-array") {
                val array = call.receive<ByteArray>()
                call.respondText("array: ${array.size}")
            }
            post("/string") {
                val text = call.receive<String>()
                call.respondText("text: $text")
            }
            post("/parameters") {
                val receivedParameters = call.receiveParameters()
                call.respondText(receivedParameters.toString())
            }
        }

        client.post("/byte-channel", { setBody("123") }).let { response ->
            assertEquals("bytes: 3", response.bodyAsText())
        }

        client.post("/byte-array", { setBody("123") }).let { response ->
            assertEquals("array: 3", response.bodyAsText())
        }

        client.post("/string", { setBody("123") }).let { response ->
            assertEquals("text: 123", response.bodyAsText())
        }

        client.post("/parameters") {
            setBody("k=v")
            header(
                HttpHeaders.ContentType,
                ContentType.Application.FormUrlEncoded.toString()
            )
        }.let { response ->
            assertEquals("Parameters [k=[v]]", response.bodyAsText())
        }
    }

    @Test
    fun testReceiveTextIgnoresContentNegotiation() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Any, alwaysFailingConverter(false))
        }

        routing {
            post("/text") {
                val text = call.receiveText()
                call.respondText("text: $text")
            }
        }

        client.post("/text") {
            setBody("\"k=v\"")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertEquals("text: \"k=v\"", response.bodyAsText())
        }
    }

    @Test
    fun testRespondByteReadChannelIgnoresContentNegotiation() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Any, alwaysFailingConverter(false))
        }

        routing {
            get("/text") {
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                call.respond(ByteReadChannel("""{"x": 123}""".toByteArray()))
            }
        }

        client.get("/text").let { response ->
            assertEquals("""{"x": 123}""", response.bodyAsText())
        }
    }

    @Test
    fun testCustomAcceptedContentTypesContributor() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Text.Plain, textContentConverter)
            register(ContentType.Text.Html, textContentConverter)

            accept { call, acceptedContentTypes ->
                call.request.queryParameters["format"]?.let { format ->
                    when (format) {
                        "text" -> listOf(ContentTypeWithQuality(ContentType.Text.Plain))
                        "html" -> listOf(ContentTypeWithQuality(ContentType.Text.Html))
                        else -> null
                    }
                } ?: acceptedContentTypes
            }
        }

        routing {
            get("/") {
                call.respond(Wrapper("test content"))
            }
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/plain")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/html")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/plain, text/html")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/plain; q=0.9, text/html")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }

        client.get("/?format=html") {
            header(HttpHeaders.Accept, "text/plain")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }

        client.get("/?format=text") {
            header(HttpHeaders.Accept, "text/html")
        }.let { response ->
            assertEquals("test content", response.bodyAsText())
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        }
    }

    @Test
    fun testDoubleReceive() = testApplication {
        install(DoubleReceive)
        install(ContentNegotiation) {
            register(ContentType.Text.Plain, textContentConverter)
        }

        routing {
            get("/") {
                call.respondText(call.receive<Wrapper>().value + "-" + call.receive<Wrapper>().value)
            }
        }

        client.get("/?format=text") {
            header(HttpHeaders.Accept, "text/plain")
            header(HttpHeaders.ContentType, "text/plain")
            setBody("[content]")
        }.let { response ->
            assertEquals("[content]-[content]", response.bodyAsText())
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        }
    }

    @Test
    fun testIllegalAcceptAndContentTypes() = testApplication {
        var serializeCalled = false
        var deserializeCalled = false
        install(ContentNegotiation) {
            register(
                ContentType.Text.Plain,
                object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent? {
                        serializeCalled = true
                        return null
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any? {
                        deserializeCalled = true
                        return null
                    }
                }
            )
        }

        routing {
            get("/receive") {
                call.receive<String>()
                call.response.header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                call.respond("ok")
            }
            get("/send") {
                call.response.header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                call.respond("ok")
            }
        }

        client.get("/receive") {
            header("Content-Type", ContentType.Application.OctetStream)
            setBody("any")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }

        client.get("/send") {
            header("Content-Type", ContentType.Application.OctetStream)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }

        assertEquals(false, serializeCalled)
        assertEquals(false, deserializeCalled)
    }

    @Test
    fun testIllegalAcceptAndCheckAcceptHeader() = testApplication {
        install(ContentNegotiation) {
            checkAcceptHeaderCompliance = true
            register(ContentType.Text.Plain, textContentConverter)
        }

        routing {
            get("/send") {
                assertFailsWith<BadRequestException> {
                    call.respond(Any())
                }.let { throw it }
            }
        }

        client.get("/send") {
            header("Accept", "....aa..laa...laa")
        }.let { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun testMatchingAcceptAndContentTypes() = testApplication {
        install(ContentNegotiation) {
            checkAcceptHeaderCompliance = true
        }

        routing {
            get("/send") {
                call.respond("some text")
            }
        }

        client.get("/send") {
            header("Accept", "text/plain")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.get("/send") {
            header("Accept", "application/json, text/plain;q=0.1")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        client.get("/send") {
            header("Accept", "*/*")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.get("/send") {
            header("Accept", "text/*")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testWithCharset() = testApplication {
        install(ContentNegotiation) {
            clearIgnoredTypes()
            register(
                contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8),
                converter = object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent {
                        return TextContent("$value!", contentType)
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any {
                        content.readRemaining().readText().let { text ->
                            return text.substring(0, text.length - 1)
                        }
                    }
                }
            )
        }

        routing {
            post {
                val request = call.receive<String>()
                assertEquals("text", request)
                call.respond(request)
            }
        }

        val response = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("text!")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
        assertEquals("text!", response.bodyAsText())
    }

    @Test
    fun testMultipleConvertersWithSameType() = testApplication {
        var nullRequestDeserialized = false
        var requestDeserialized = false
        var nullResponseSerializeAttempted = false
        var responseSerialized = false

        data class User(val name: String)

        install(ContentNegotiation) {
            register(
                ContentType.Application.Json,
                object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent? {
                        nullResponseSerializeAttempted = true
                        return null
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any? {
                        nullRequestDeserialized = true
                        return null
                    }
                }
            )
            register(
                ContentType.Application.Json,
                object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent {
                        responseSerialized = true
                        check(value is User)
                        return TextContent(value.name, contentType)
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any {
                        requestDeserialized = true
                        return User(content.readRemaining().readText())
                    }
                }
            )
        }

        routing {
            post("/") {
                val user = call.receive<User>()
                call.respond(user)
            }
        }

        client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("Kotlin")
        }.let { response ->
            assertTrue(nullRequestDeserialized)
            assertTrue(requestDeserialized)

            assertTrue(nullResponseSerializeAttempted)
            assertTrue(responseSerialized)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Kotlin", response.bodyAsText())
        }
    }
}
