/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class ApplicationRequestContentTest {
    @Test
    fun testSimpleStringContent() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveText())
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testSimpleStringContentWithBadContentType() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertFailsWith<BadRequestException> {
                    call.receiveText()
                }.let { throw it }
            }
        }

        createClient { useDefaultTransformers = false }.get("") {
            header(HttpHeaders.ContentType, "...la..la..la")
            setBody(ByteArrayContent("any".encodeToByteArray()))
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.status)
        }
    }

    @Test
    fun testStringValues() = testApplication {
        val values = parametersOf("a", "1")

        application {
            intercept(ApplicationCallPipeline.Call) {
                val actual = call.receiveParameters()
                assertEquals(values, actual)
            }
        }

        client.post("") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            val value = values.formUrlEncode()
            setBody(value)
        }
    }

    @Test
    fun testIllegalContentType() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertFailsWith<BadRequestException> {
                    call.receiveParameters()
                }.let { throw it }
            }
        }

        createClient { useDefaultTransformers = false }.post("") {
            header(HttpHeaders.ContentType, "...la..la..la")
            setBody(ByteArrayContent("don't care".encodeToByteArray()))
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.status)
        }
    }

    @Test
    fun testStringValuesWithCharset() = testApplication {
        val values = parametersOf("a", "1")

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }
        }

        client.post("") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
            setBody(values.formUrlEncode())
        }
    }

    @Test
    fun testCustomTransform() = testApplication {
        val value = IntList(listOf(1, 2, 3, 4))

        application {
            receivePipeline.intercept(ApplicationReceivePipeline.Transform) { body ->
                if (call.receiveType != typeInfo<IntList>()) return@intercept
                val message = body as? ByteReadChannel ?: return@intercept

                val string = message.readRemaining().readText()
                val transformed = IntList.parse(string)
                proceedWith(transformed)
            }
        }

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(value, call.receive())
            }
        }

        client.get("") {
            setBody(value.toString())
        }
    }

    @Test
    fun testFormUrlEncodedContent() = testApplication {
        val values = parametersOf(
            "one" to listOf("1"),
            "two_space_three_and_four" to listOf("2 3 & 4")
        )
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }
        }

        client.post("") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(values.formUrlEncode())
        }
    }

    @Test
    fun testReceiveUnsupportedTypeFailing() = testApplication {
        install(ContentNegotiation)

        routing {
            get("/") {
                val v = call.receive<IntList>()
                call.respondText(v.values.joinToString())
            }
        }

        assertEquals(415, client.get("/").status.value)
    }

    @Test
    fun testReceiveUnsupportedTypeNotFailing() = testApplication {
        install(ContentNegotiation)

        routing {
            get("/") {
                val v = runCatching { call.receiveNullable<IntList>() }.getOrNull()
                call.respondText(v?.values?.joinToString() ?: "(none)")
            }
        }

        assertEquals(200, client.get("/").status.value)
    }

    @Test
    fun testDoubleReceiveWithNoPlugin() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveText())
                assertFailsWith<RequestAlreadyConsumedException> {
                    call.receiveText()
                }
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveDifferentTypes() = testApplication {
        install(DoubleReceive)

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent".toByteArray().toList(), call.receive<ByteArray>().toList())
                assertEquals("bodyContent", call.receiveText())

                // this also works because we already have a byte array cached
                assertEquals("bodyContent", call.receiveChannel().readUTF8Line())
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testDoubleReceiveChannels() = testApplication {
        install(DoubleReceive)

        application {
            intercept(ApplicationCallPipeline.Call) {
                call.receiveChannel().readRemaining().use { packet ->
                    assertEquals(11, packet.remaining)
                }
                call.receiveChannel().readRemaining().use { packet ->
                    assertEquals(11, packet.remaining)
                }
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveAfterTransformationFailed() = testApplication {
        install(DoubleReceive)

        application {
            receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                if (call.receiveType.type == IntList::class) {
                    throw MySpecialException()
                }
            }
            intercept(ApplicationCallPipeline.Call) {
                assertFailsWith<MySpecialException> {
                    call.receive<IntList>()
                }
                assertFailsWith<MySpecialException> {
                    call.receive<IntList>()
                }
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }
}

data class IntList(val values: List<Int>) {
    override fun toString() = "$values"

    companion object {
        fun parse(text: String) =
            IntList(text.removeSurrounding("[", "]").split(",").map { it.trim().toInt() })
    }
}

private class MySpecialException : Exception("Expected exception")
