/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

public object HxRequestHeaders {
    /**
     * indicates that the request is via an element using hx-boost
     */
    public const val Boosted: String = "HX-Boosted"

    /**
     * the current URL of the browser
     */
    public const val CurrentURL: String = "HX-Current-URL"

    /**
     * “true” if the request is for history restoration after a miss in the local history cache
     */
    public const val HistoryRestoreRequest: String = "HX-History-Restore-Request"

    /**
     * the user response to an hx-prompt
     */
    public const val Prompt: String = "HX-Prompt"

    /**
     * always “true”
     */
    public const val Request: String = "HX-Request"

    /**
     * the id of the target element if it exists
     */
    public const val Target: String = "HX-Target"

    /**
     * the name of the triggered element if it exists
     */
    public const val TriggerName: String = "HX-Trigger-Name"

    /**
     * the id of the triggered element if it exists
     */
    public const val Trigger: String = "HX-Trigger"
}
