/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.utils.io.*
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import kotlin.coroutines.CoroutineContext

@InternalAPI
public class JettyApplicationCall(
    application: Application,
    request: Request,
    response: Response,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application) {

    override val request: JettyApplicationRequest = JettyApplicationRequest(this, request)
    override val response: JettyApplicationResponse =
        JettyApplicationResponse(this, request, response, coroutineContext)

    init {
        putResponseAttribute()
    }
}
