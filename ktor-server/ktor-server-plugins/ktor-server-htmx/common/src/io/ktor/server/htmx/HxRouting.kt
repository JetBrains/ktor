/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import io.ktor.htmx.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmInline

@ExperimentalHtmxApi
public val Route.hx: HXRoute get() = HXRoute(this)

/**
 * Scope child routes to apply when `HX-Request` header is supplied.
 */
@ExperimentalHtmxApi
public fun Route.hx(configuration: HXRoute.() -> Unit): Route = with(HXRoute(this)) {
    header(HxRequestHeaders.Request, "true") {
        configuration()
    }
}

/**
 * Provides custom routes based on common HTMX headers.
 */
@ExperimentalHtmxApi
@KtorDsl
@JvmInline
public value class HXRoute(private val route: Route) : Route by route {

    /**
     * Sub-routes only apply to a specific HX-Target header.
     */
    public fun target(expectedTrigger: String, body: Route.() -> Unit): Route =
        header(HxRequestHeaders.Target, expectedTrigger, body)

    /**
     * Sub-routes only apply to a specific HX-Trigger header.
     */
    public fun trigger(expectedTrigger: String, body: Route.() -> Unit): Route =
        header(HxRequestHeaders.Trigger, expectedTrigger, body)
}
