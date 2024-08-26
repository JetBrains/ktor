/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.jetty

import io.ktor.http.*
import io.ktor.server.jetty.internal.*
import io.ktor.server.response.*
import io.ktor.server.servlet.*
import io.ktor.utils.io.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.*

@InternalAPI
public class JettyApplicationResponse(
    call: AsyncServletApplicationCall,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext,
    private val baseRequest: Request,
    coroutineContext: CoroutineContext
) : AsyncServletApplicationResponse(
    call,
    servletRequest,
    servletResponse,
    engineContext,
    userContext,
    JettyUpgradeImpl,
    coroutineContext
) {

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder) {
        if (baseRequest.isPushSupported) {
            baseRequest.pushBuilder.apply {
                this.method(builder.method.value)
                this.path(builder.url.encodedPath)
                val query = builder.url.buildString().substringAfter('?', "").takeIf { it.isNotEmpty() }
                if (query != null) {
                    queryString(query)
                }

                push()
            }
        } else {
            super.push(builder)
        }
    }
}
