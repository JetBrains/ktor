/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.csrf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*

/**
 * A configuration for the [io.ktor.server.plugins.csrf.CSRF] plugin.
 */
@KtorDsl
public class CSRFConfig {
    internal var originMatchesHost = false
    internal val allowedOrigins = mutableListOf<Url>()
    internal val headerChecks = mutableMapOf<String, HeaderPredicate>()
    internal var handleFailure = respondBadRequestIfNotCommitted

    /**
     * All incoming requests must have an "Origin" header matching one of the hosts
     * defined using this method.
     *
     * @param origin expected "Origin" header, revealing the URL of the site leading up
     *               to the path (e.g. https://google.com)
     * @see [CSRF Cheatsheet, Verifying Origin with standard headers](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#verifying-origin-with-standard-headers)
     */
    public fun allowOrigin(origin: String) {
        allowedOrigins += Url(origin)
    }

    /**
     * Checks if the "Origin" header has the same host as submitted in the "Host"
     * header.  This avoids needing to configure the expected host name where your
     * application is deployed but will not work when it is deployed behind a proxy.
     *
     * @see [CSRF Cheatsheet, Identifying the target origin](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#identifying-the-target-origin)
     */
    public fun originMatchesHost() {
        originMatchesHost = true
    }

    /**
     * Checks if the given header is present on each call to the server, and if its value
     * conforms to the optional predicate.  If conditions already exist for the header, they
     * must all be satisfied.
     *
     * @param header the name of the header to validate
     * @param predicate the condition to check on the value of the header
     * @see [CSRF Cheatsheet, Custom request headers](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#custom-request-headers)
     */
    public fun checkHeader(header: String, predicate: ApplicationCall.(String) -> Boolean = { true }) {
        headerChecks[header] = headerChecks[header]?.let { it and predicate } ?: predicate
    }

    /**
     * Handle CSRF error conditions.  By default, a 400 response is returned with a string response
     * containing the error.  As with any security-related error, it is advised to log the problem and
     * return some generic response.
     *
     * @param handleFailure handler for CSRF error conditions
     */
    public fun onFailure(handleFailure: suspend ApplicationCall.(String) -> Unit) {
        this.handleFailure = handleFailure + respondBadRequestIfNotCommitted
    }
}

private typealias HeaderPredicate = ApplicationCall.(String) -> Boolean
private infix fun HeaderPredicate.and(other: HeaderPredicate): HeaderPredicate = { this@and(it) && other(it) }
internal typealias ErrorMessageHandler = suspend ApplicationCall.(String) -> Unit

internal val respondBadRequestIfNotCommitted: ErrorMessageHandler = { message ->
    if (!response.isCommitted) {
        respond(HttpStatusCode.BadRequest, "Cross-site request validation failed; $message")
    }
}
internal operator fun ErrorMessageHandler.plus(other: ErrorMessageHandler): ErrorMessageHandler = { message ->
    this@plus(message)
    other(message)
}
