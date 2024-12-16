/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

public object HxCss {
    /**
     * Applied to a new piece of content before it is swapped, removed after it is settled.
     */
    public const val Added: String = "htmx-added"

    /**
     * A dynamically generated class that will toggle visible (opacity:1) when a htmx-request class is present
     */
    public const val Indicator: String = "htmx-indicator"

    /**
     * Applied to either the element or the element specified with hx-indicator while a request is ongoing
     */
    public const val Request: String = "htmx-request"

    /**
     * Applied to a target after content is swapped, removed after it is settled. The duration can be modified via hx-swap.
     */
    public const val Settling: String = "htmx-settling"

    /**
     * Applied to a target before any content is swapped, removed after it is swapped. The duration can be modified via hx-swap.
     */
    public const val Swapping: String = "htmx-swapping"
}
