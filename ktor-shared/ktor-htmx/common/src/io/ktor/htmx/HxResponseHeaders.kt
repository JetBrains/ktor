/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

public object HxResponseHeaders {
    /**
     * allows you to do a client-side redirect that does not do a full page reload
     */
    public const val Location: String = "HX-Location"

    /**
     * pushes a new url into the history stack
     */
    public const val PushUrl: String = "HX-Push-Url"

    /**
     * can be used to do a client-side redirect to a new location
     */
    public const val Redirect: String = "HX-Redirect"

    /**
     * if set to “true” the client-side will do a full refresh of the page
     */
    public const val Refresh: String = "HX-Refresh"

    /**
     * replaces the current URL in the location bar
     */
    public const val ReplaceUrl: String = "HX-Replace-Url"

    /**
     * allows you to specify how the response will be swapped. See hx-swap for possible values
     */
    public const val Reswap: String = "HX-Reswap"

    /**
     * a CSS selector that updates the target of the content update to a different element on the page
     */
    public const val Retarget: String = "HX-Retarget"

    /**
     * a CSS selector that allows you to choose which part of the response is used to be swapped in. Overrides an existing hx-select on the triggering element
     */
    public const val Reselect: String = "HX-Reselect"

    /**
     * allows you to trigger client-side events
     */
    public const val Trigger: String = "HX-Trigger"

    /**
     * allows you to trigger client-side events after the settle step
     */
    public const val TriggerAfterSettle: String = "HX-Trigger-After-Settle"

    /**
     * allows you to trigger client-side events after the swap step
     */
    public const val TriggerAfterSwap: String = "HX-Trigger-After-Swap"
}
