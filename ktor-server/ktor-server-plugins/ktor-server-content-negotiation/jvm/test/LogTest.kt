/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import ch.qos.logback.classic.*
import ch.qos.logback.classic.spi.*
import ch.qos.logback.core.read.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class LogTest {
    @Test
    fun ignoredTypeMessageForResponseBody() = testApplication {
        val logger = LOGGER as Logger
        logger.level = Level.ALL

        val listAppender = ListAppender<ILoggingEvent>()
        logger.addAppender(listAppender)
        listAppender.start()

        val plugin = createRouteScopedPlugin("PartialContentNegotiation", ::ContentNegotiationConfig) {
            convertResponseBody()
        }

        application {
            install(plugin)
            routing {
                get("/") {
                    call.respond("test")
                }
            }
        }

        client.get("/")

        listAppender.stop()
        assertEquals(
            "Skipping response body transformation from String to OutgoingContent for the GET / request" +
                " because the String type is ignored. See [ContentNegotiationConfig::ignoreType].",
            listAppender.list[0].message
        )
    }
}
