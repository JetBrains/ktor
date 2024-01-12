/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package test.server.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import java.util.concurrent.atomic.*

internal val counter = AtomicInteger(0)

internal fun Application.cacheTestServer() {
    routing {
        route("/cache") {
            install(CachingHeaders)
            install(ConditionalHeaders)

            get("/no-cache") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondText("$value")
            }
            get("/no-store") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoStore(null))
                call.respondText("$value")
            }
            get("/max-age") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.MaxAge(2))
                call.respondText("$value")
            }
            get("/expires") {
                // note: we don't add X-Expires to Vary intentionally
                val value = counter.incrementAndGet()
                call.response.header(HttpHeaders.Expires, call.request.headers["X-Expires"] ?: "?")
                call.respondText("$value")
            }

            /**
             * Return same etag for the first 2 responses.
             */
            get("/etag") {
                val maxAge = call.request.queryParameters["max-age"]?.toIntOrNull()
                val current = counter.incrementAndGet()
                if (maxAge != null) call.response.cacheControl(CacheControl.MaxAge(maxAge))
                call.response.etag("0")
                call.respondText(current.toString())
            }

            get("/etag-304") {
                if (call.request.header("If-None-Match") == "My-ETAG") {
                    call.response.header("Etag", "My-ETAG")
                    call.response.header("Vary", "Origin")
                    call.respond(HttpStatusCode.NotModified)
                    return@get
                }

                call.response.header("Etag", "My-ETAG")
                call.response.header("Vary", "Origin, Accept-Encoding")
                call.respondText(contentType = ContentType.Application.Json) { "{}" }
            }

            get("/last-modified") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain)
                response.versions += LastModifiedVersion(GMTDate.START)

                call.respond(response)
            }

            get("/vary") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(60))
                }
                response.versions += LastModifiedVersion(GMTDate.START)

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/vary-stale") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(0))
                }
                response.versions += LastModifiedVersion(GMTDate.START)

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/public") {
                call.response.cacheControl(CacheControl.MaxAge(60))
                call.respondText("public")
            }
            get("/private") {
                call.response.cacheControl(CacheControl.MaxAge(60, visibility = CacheControl.Visibility.Private))
                call.respondText("private")
            }
            get("/cache_${"a".repeat(3000)}") {
                call.respondText { "abc" }
            }
            get("/set-max-age") {
                call.response.header(HttpHeaders.CacheControl, "max-age=${Long.MAX_VALUE}000")
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
