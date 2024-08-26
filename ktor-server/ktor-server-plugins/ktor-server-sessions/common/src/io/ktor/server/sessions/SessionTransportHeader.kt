/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * A session transport that sets or gets the specific header [name],
 * applying/un-applying the specified transforms defined by [transformers].
 *
 * @property name is a header name
 * @property transformers is a list of registered session transformers
 */
public class SessionTransportHeader(
    public val name: String,
    public val transformers: List<SessionTransportTransformer>
) : SessionTransport {
    init {
        HttpHeaders.checkHeaderName(name)
    }

    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.headers[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        call.response.header(name, transformers.transformWrite(value))
    }

    override fun clear(call: ApplicationCall) {}

    override fun toString(): String {
        return "SessionTransportHeader: $name"
    }
}
