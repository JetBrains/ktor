/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConstPropertyName")

package io.ktor.htmx

public object HxSwap {
    /**
     * Replace the inner HTML of the target element
     */
    public const val innerHTML: String = "innerHtml"

    /**
     * Replace the entire target element with the response
     */
    public const val outerHTML: String = "outerHTML"

    /**
     * Replace the text content of the target element, without parsing the response as HTML
     */
    public const val textContent: String = "textContent"

    /**
     * Insert the response before the target element
     */
    public const val beforeBegin: String = "beforebegin"

    /**
     * Insert the response before the first child of the target element
     */
    public const val afterBegin: String = "afterbegin"

    /**
     * Insert the response after the last child of the target element
     */
    public const val beforeEnd: String = "beforeend"

    /**
     * Insert the response after the target element
     */
    public const val afterEnd: String = "afterend"

    /**
     * Deletes the target element regardless of the response
     */
    public const val delete: String = "delete"

    /**
     * Does not append content from response (out of band items will still be processed)
     */
    public const val none: String = "none"
}
