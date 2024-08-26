/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlin.test.*

class WebSocketWithContentNegotiationTest {

    @Test
    fun test(): Unit = withTestApplication {
        application.install(WebSockets)
        application.install(ContentNegotiation) {
            val converter = object : ContentConverter {
                override suspend fun serialize(
                    contentType: ContentType,
                    charset: Charset,
                    typeInfo: TypeInfo,
                    value: Any?
                ): OutgoingContent = fail("convertForSend shouldn't be invoked")

                override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
                    fail("convertForReceive shouldn't be invoked")
                }
            }

            register(ContentType.Any, converter)
        }

        application.routing {
            webSocket("/") {
                outgoing.send(Frame.Text("OK"))
            }
        }

        handleWebSocketConversation("/", {}) { incoming, _ ->
            incoming.receive()
        }
    }
}
