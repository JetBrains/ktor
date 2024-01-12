/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.engine

import io.ktor.client.engine.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.test.*

@OptIn(InternalAPI::class)
class UtilsTest {
    @Test
    fun testMergeHeaders() {
        val headers = HeadersBuilder().apply {
            append("Accept", "application/xml")
            append("Accept", "application/json")
        }

        val result = mutableMapOf<String, String>()
        mergeHeaders(headers.build(), EmptyContent) { key, value ->
            result[key] = value
        }

        assertEquals("application/xml,application/json", result["Accept"])
    }

    @Test
    fun testDateHeadersAreNotMerged() {
        val date = GMTDate().toHttpDate()
        val headers = HeadersBuilder().apply {
            append(HttpHeaders.Date, date)
            append(HttpHeaders.Date, date)
            append(HttpHeaders.LastModified, date)
            append(HttpHeaders.LastModified, date)
        }

        val result = buildHeaders {
            mergeHeaders(headers.build(), EmptyContent) { key, value ->
                append(key, value)
            }
        }

        val dateHeader = result.getAll(HttpHeaders.Date) ?: fail()
        assertEquals(2, dateHeader.size)

        dateHeader.forEach { assertEquals(date, it) }

        val lastModifiedHeader = result.getAll(HttpHeaders.LastModified) ?: fail()
        assertEquals(2, lastModifiedHeader.size)

        lastModifiedHeader.forEach { assertEquals(date, it) }
    }
}
