/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.test.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DefaultTransformJvmTest {

    @Test
    fun testSendInputStream() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    val text = (it.body as OutgoingContent.ReadChannelContent).readFrom().readRemaining().readText()
                    respond(text)
                }
            }
        }

        test { client ->
            val response = client.post("/post") {
                val stream = ByteArrayInputStream("""{"x": 123}""".toByteArray())
                contentType(ContentType.Application.Json)
                setBody(stream)
            }.bodyAsText()
            assertEquals("""{"x": 123}""", response)
        }
    }

    @Test
    fun testReceiveInputStream() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    respond("""{"x": 123}""")
                }
            }
        }

        test { client ->
            val response = client.get("/").body<InputStream>()
            assertEquals("""{"x": 123}""", response.bufferedReader().readText())
        }
    }
}
