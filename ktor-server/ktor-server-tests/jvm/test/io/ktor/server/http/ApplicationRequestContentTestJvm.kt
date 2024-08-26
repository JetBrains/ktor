// ktlint-disable filename
/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationRequestContentTest {

    @Test
    fun testInputStreamContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveStream().reader(Charsets.UTF_8).readText())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody("bodyContent")
            }
        }
    }

    @Test
    fun testDoubleReceiveStreams(): Unit = withTestApplication {
        application.install(DoubleReceive)

        application.intercept(ApplicationCallPipeline.Call) {
            assertEquals(11, call.receiveStream().readBytes().size)
            assertEquals(11, call.receiveStream().readBytes().size)
        }

        handleRequest(HttpMethod.Get, "") {
            setBody("bodyContent")
        }
    }
}
