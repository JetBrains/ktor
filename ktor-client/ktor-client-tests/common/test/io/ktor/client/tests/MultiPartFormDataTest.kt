/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.*

/**
 * Tests client request with multi-part form data.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.tests.MultiPartFormDataTest)
 */
class MultiPartFormDataTest : ClientLoader() {

    private val fileDispositionHeaders = Headers.build {
        append(
            HttpHeaders.ContentDisposition,
            """form-data; name="file"; filename="test.png""""
        )
    }

    @Test
    fun testMultiPartFormData() = clientTests(listOf("native")) {
        test { client ->
            val result = client.preparePost("$TEST_SERVER/multipart") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", ByteArray(1024 * 1024), fileDispositionHeaders)
                        }
                    )
                )
            }.execute()

            assertEquals(HttpStatusCode.OK, result.status)
        }
    }

    @Test
    fun testEmptyMultiPartFormData() = clientTests {
        test { client ->
            val response = client.submitFormWithBinaryData("$TEST_SERVER/multipart/empty", emptyList())
            assertTrue(response.status.isSuccess())
        }
    }
}
