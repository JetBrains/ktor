/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.csrf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * This plugin provides mitigations for cross-site request forgery (CSRF).
 *
 * There are several ways to prevent CSRF attacks, each with different pros / cons depending on how
 * your website is structured.  The [OWASP cheatsheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
 * enumerates the mitigations provided here.
 *
 * Example:
 *
 *  ```kotlin
 *  install(CSRF) {
 *      // tests Origin is an expected value
 *      allowOrigin("http://localhost:8080")
 *
 *      // tests Origin matches Host header
 *      originMatchesHost()
 *
 *      // custom header checks
 *      checkHeader("X-CSRF-Token")
 *  }
 *  ```
 *
 * @see io.ktor.server.sessions.SameSite for preventing cookies from being used when navigating from external sites
 */
public val CSRF: RouteScopedPlugin<CSRFConfig> = createRouteScopedPlugin("CSRF", ::CSRFConfig) {
    val checkHost = pluginConfig.originMatchesHost
    val allowedOrigins = pluginConfig.allowedOrigins
    val headerChecks = pluginConfig.headerChecks
    val onFailure = pluginConfig.handleFailure

    onCall { call ->

        // Host standard header matches the Origin
        if (checkHost) {
            val origin = call.originOrReferrerUrl() ?: return@onCall onFailure(call, "missing \"Origin\" header")
            val host = call.request.headers[HttpHeaders.Host]
            if (host != origin.hostWithPortIfSpecified) {
                return@onCall onFailure(
                    call,
                    "expected \"Origin\" [${origin.host}] host to match \"Host\" [$host] header value"
                )
            }
        }

        // Same origin with standard headers, Origin with Referrer fallback
        if (allowedOrigins.isNotEmpty()) {
            val origin = call.originOrReferrerUrl(allowedOrigins.first().protocol)
                ?: return@onCall onFailure(call, "missing \"Origin\" header")
            if (origin !in allowedOrigins) {
                return@onCall onFailure(call, "unexpected \"Origin\" header value [$origin]")
            }
        }

        // Custom header checks
        if (headerChecks.isNotEmpty()) {
            for ((header, check) in headerChecks) {
                val headerValue =
                    call.request.headers[header] ?: return@onCall onFailure(call, "missing \"$header\" header")
                if (!check(call, headerValue)) {
                    return@onCall onFailure(call, "unexpected \"$header\" header value [$headerValue]")
                }
            }
        }
    }
}

private fun ApplicationCall.originOrReferrerUrl(expectedProtocol: URLProtocol? = null): Url? =
    request.originHeader() ?: request.referrerNormalized(expectedProtocol)

private fun ApplicationRequest.originHeader(): Url? =
    headers[HttpHeaders.Origin]?.let(::Url)

/**
 * Gets the value of the Referrer header and 1) removes path, and 2) replaces protocol with the expected. This
 * is done because Referrer is most commonly found in requests to http pages that redirect to https.
 *
 * @see [Checking the Referrer header](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#checking-the-referer-header)
 */
private fun ApplicationRequest.referrerNormalized(expectedProtocol: URLProtocol? = null): Url? =
    headers[HttpHeaders.Referrer]?.let { referrer ->
        URLBuilder().takeFrom(referrer).apply {
            encodedPath = ""
            expectedProtocol?.let {
                protocol = expectedProtocol
            }
        }.build()
    }
