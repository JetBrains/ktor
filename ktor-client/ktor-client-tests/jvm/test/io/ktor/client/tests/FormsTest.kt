/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import java.io.*
import kotlin.test.*

class FormsTest {

    @Test
    fun testEmptyFormData() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    val content = it.body.toByteReadPacket()
                    respondOk(content.readText())
                }
            }
        }

        test { client ->
            val input = object : InputStream() {
                override fun read(): Int = -1
            }.asInput()

            val builder = HttpRequestBuilder().apply {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            appendInput(
                                "file",
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=myfile.txt")
                                }
                            ) { input }
                        }
                    )
                )
            }

            client.request(builder).body<String>()
        }
    }
}
