/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.htmx.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalHtmxApi
class HtmxTest {

    @Test
    fun htmxAttributes() {
        val actual = buildString {
            appendHTML(prettyPrint = true).html {
                body {
                    button {
                        attributes.hx {
                            get = "/?page=1"
                            target = "#replaceMe"
                            swap = HxSwap.outerHTML
                            trigger = "click[console.log('Hello!')||true]"
                        }
                    }
                }
            }
        }
        assertEquals(
            """
            <html>
              <body><button hx-get="/?page=1" hx-target="#replaceMe" hx-swap="outerHTML" hx-trigger="click[console.log('Hello!')||true]"></button></body>
            </html>
            """.trimIndent(),
            actual.trim()
        )
    }

    @Test
    fun routing() = testApplication {
        val responseTemplate: (String) -> String = { header ->
            """
                <!DOCTYPE html>
                <html>
                  <body>
                    <h1>$header</h1>
                  </body>
                </html>
            """.trimIndent()
        }

        routing {
            val respondWith: suspend RoutingContext.(String) -> Unit = { text ->
                call.respondHtml {
                    body {
                        h1 { +text }
                    }
                }
            }
            route("htmx") {
                hx.get {
                    respondWith("No target")
                }
                hx {
                    target("#test") {
                        get {
                            respondWith("With target")
                        }
                    }
                    trigger("#button") {
                        get {
                            respondWith("With trigger")
                        }
                    }
                }
            }
        }
        assertEquals(
            responseTemplate("No target"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
            }.bodyAsText().trim()
        )
        assertEquals(
            responseTemplate("With target"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
                headers[HxRequestHeaders.Target] = "#test"
            }.bodyAsText().trim()
        )
        assertEquals(
            responseTemplate("With trigger"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
                headers[HxRequestHeaders.Trigger] = "#button"
            }.bodyAsText().trim()
        )
    }
}
