/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.htmx

public object HxEvents {
    /**
     * send this event to an element to abort a request
     */
    public const val Abort: String = "htmx:abort"

    /**
     * triggered after an AJAX request has completed processing a successful response
     */
    public const val AfterOnLoad: String = "htmx:afterOnLoad"

    /**
     * triggered after htmx has initialized a node
     */
    public const val AfterProcessNode: String = "htmx:afterProcessNode"

    /**
     * triggered after an AJAX request has completed
     */
    public const val AfterRequest: String = "htmx:afterRequest"

    /**
     * triggered after the DOM has settled
     */
    public const val AfterSettle: String = "htmx:afterSettle"

    /**
     * triggered after new content has been swapped in
     */
    public const val AfterSwap: String = "htmx:afterSwap"

    /**
     * triggered before htmx disables an element or removes it from the DOM
     */
    public const val BeforeCleanupElement: String = "htmx:beforeCleanupElement"

    /**
     * triggered before any response processing occurs
     */
    public const val BeforeOnLoad: String = "htmx:beforeOnLoad"

    /**
     * triggered before htmx initializes a node
     */
    public const val BeforeProcessNode: String = "htmx:beforeProcessNode"

    /**
     * triggered before an AJAX request is made
     */
    public const val BeforeRequest: String = "htmx:beforeRequest"

    /**
     * triggered before a swap is done, allows you to configure the swap
     */
    public const val BeforeSwap: String = "htmx:beforeSwap"

    /**
     * triggered just before an ajax request is sent
     */
    public const val BeforeSend: String = "htmx:beforeSend"

    /**
     * triggered before the request, allows you to customize parameters, headers
     */
    public const val ConfigRequest: String = "htmx:configRequest"

    /**
     * triggered after a trigger occurs on an element, allows you to cancel (or delay) issuing the AJAX request
     */
    public const val Confirm: String = "htmx:confirm"

    /**
     * triggered on an error during cache writing
     */
    public const val HistoryCacheError: String = "htmx:historyCacheError"

    /**
     * triggered on a cache miss in the history subsystem
     */
    public const val HistoryCacheMiss: String = "htmx:historyCacheMiss"

    /**
     * triggered on a unsuccessful remote retrieval
     */
    public const val HistoryCacheMissError: String = "htmx:historyCacheMissError"

    /**
     * triggered on a successful remote retrieval
     */
    public const val HistoryCacheMissLoad: String = "htmx:historyCacheMissLoad"

    /**
     * triggered when htmx handles a history restoration action
     */
    public const val HistoryRestore: String = "htmx:historyRestore"

    /**
     * triggered before content is saved to the history cache
     */
    public const val BeforeHistorySave: String = "htmx:beforeHistorySave"

    /**
     * triggered when new content is added to the DOM
     */
    public const val Load: String = "htmx:load"

    /**
     * triggered when an element refers to a SSE event in its trigger, but no parent SSE source has been defined
     */
    public const val NoSSESourceError: String = "htmx:noSSESourceError"

    /**
     * triggered when an exception occurs during the onLoad handling in htmx
     */
    public const val OnLoadError: String = "htmx:onLoadError"

    /**
     * triggered after an out of band element as been swapped in
     */
    public const val OobAfterSwap: String = "htmx:oobAfterSwap"

    /**
     * triggered before an out of band element swap is done, allows you to configure the swap
     */
    public const val OobBeforeSwap: String = "htmx:oobBeforeSwap"

    /**
     * triggered when an out of band element does not have a matching ID in the current DOM
     */
    public const val OobErrorNoTarget: String = "htmx:oobErrorNoTarget"

    /**
     * triggered after a prompt is shown
     */
    public const val Prompt: String = "htmx:prompt"

    /**
     * triggered after an url is pushed into history
     */
    public const val PushedIntoHistory: String = "htmx:pushedIntoHistory"

    /**
     * triggered when an HTTP response error (non-200 or 300 response code) occurs
     */
    public const val ResponseError: String = "htmx:responseError"

    /**
     * triggered when a network error prevents an HTTP request from happening
     */
    public const val SendError: String = "htmx:sendError"

    /**
     * triggered when an error occurs with a SSE source
     */
    public const val SSEError: String = "htmx:sseError"

    /**
     * triggered when a SSE source is opened
     */
    public const val SSEOpen: String = "htmx:sseOpen"

    /**
     * triggered when an error occurs during the swap phase
     */
    public const val SwapError: String = "htmx:swapError"

    /**
     * triggered when an invalid target is specified
     */
    public const val TargetError: String = "htmx:targetError"

    /**
     * triggered when a request timeout occurs
     */
    public const val Timeout: String = "htmx:timeout"

    /**
     * triggered before an element is validated
     */
    public const val ValidationValidate: String = "htmx:validation:validate"

    /**
     * triggered when an element fails validation
     */
    public const val ValidationFailed: String = "htmx:validation:failed"

    /**
     * triggered when a request is halted due to validation errors
     */
    public const val ValidationHalted: String = "htmx:validation:halted"

    /**
     * triggered when an ajax request aborts
     */
    public const val XhrAbort: String = "htmx:xhr:abort"

    /**
     * triggered when an ajax request ends
     */
    public const val XhrLoadend: String = "htmx:xhr:loadend"

    /**
     * triggered when an ajax request starts
     */
    public const val XhrLoadstart: String = "htmx:xhr:loadstart"

    /**
     * triggered periodically during an ajax request that supports progress events
     */
    public const val XhrProgress: String = "htmx:xhr:progress"
}
