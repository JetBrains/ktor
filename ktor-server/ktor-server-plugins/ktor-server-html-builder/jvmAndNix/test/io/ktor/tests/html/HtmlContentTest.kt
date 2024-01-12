/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.tests.html

import io.ktor.http.*
import io.ktor.server.html.HtmlContent
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlin.test.*

class HtmlContentTest {

    @Test
    fun testChannelIsCancelledAfterException() = runBlocking {
        val content = HtmlContent(HttpStatusCode.OK) {
            body {
                p { +"Hello, world!" }
                p { error("BAAM") }
                p { +"Foooo" }
            }
        }

        val channel = ByteChannel()
        var failed = false
        try {
            content.writeTo(channel)
        } catch (cause: Throwable) {
            failed = true
            assertEquals("BAAM", cause.message)
        }

        assertTrue(failed)

        var secondFail = false
        try {
            channel.readRemaining().readText()
        } catch (cause: Throwable) {
            cause.printStackTrace()
            secondFail = true
            assertEquals("BAAM", cause.message)
        }

        assertTrue(secondFail)
    }
}
