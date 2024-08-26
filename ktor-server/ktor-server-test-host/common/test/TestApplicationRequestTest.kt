/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*

class TestApplicationRequestTest {

    @Test
    fun testLongRequest() = testApplication {
        application {
            routing {
                post("/") {
                    val requestBody = call.receive<ByteReadChannel>()
                    call.respond(requestBody)
                }
            }
        }

        client.post("/") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(ByteArray(25 * 1024))
                }
            })
        }
    }
}
