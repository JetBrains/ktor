/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty11

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty11.internal.*
import io.ktor.server.servlet5.*
import io.ktor.util.*
import jakarta.servlet.http.*
import org.eclipse.jetty.server.*
import kotlin.coroutines.*

@InternalAPI
public class JettyApplicationCall(
    application: Application,
    request: Request,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext,
    coroutineContext: CoroutineContext
) : AsyncServletApplicationCall(
    application,
    servletRequest,
    servletResponse,
    engineContext,
    userContext,
    JettyUpgradeImpl,
    coroutineContext
) {

    override val response: JettyApplicationResponse = JettyApplicationResponse(
        this,
        servletRequest,
        servletResponse,
        engineContext,
        userContext,
        request,
        coroutineContext
    )

    init {
        putResponseAttribute()
    }
}
