/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors.routing

import io.ktor.server.application.*
import io.ktor.server.plugins.cors.*

/**
 * A plugin that allows you to configure handling cross-origin requests.
 * This plugin allows you to configure allowed hosts, HTTP methods, headers set by the client, and so on.
 *
 * The configuration below allows requests from the specified address and allows sending the `Content-Type` header:
 * ```kotlin
 * install(CORS) {
 *     host("0.0.0.0:8081")
 *     header(HttpHeaders.ContentType)
 * }
 * ```
 *
 * You can learn more from [CORS](https://ktor.io/docs/cors.html).
 */
public val CORS: RouteScopedPlugin<CORSConfig> = createRouteScopedPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}
