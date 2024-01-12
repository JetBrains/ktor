/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class SwaggerTest {
    @Test
    fun testSwaggerFromResources() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui.css" rel="stylesheet">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFromResourcesWithDocExpansion() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "list")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui.css" rel="stylesheet">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@4.14.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout',
                    docExpansion: 'list'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFileIsServed() = testApplication {
        routing {
            swaggerUI("openapi")
        }

        val response = client.get("/openapi/documentation.yaml")
        val body = response.bodyAsText()
        assertEquals("text/yaml; charset=UTF-8", response.contentType().toString())
        assertEquals("hello:\n  world".filter { it.isLetterOrDigit() }, body.filter { it.isLetterOrDigit() })
    }
}
