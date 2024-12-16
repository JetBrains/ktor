/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import io.ktor.htmx.HxAttributeKeys
import kotlinx.html.HtmlTagMarker
import kotlinx.html.impl.DelegatingMap
import kotlin.jvm.JvmInline

@ExperimentalHtmxApi
public val DelegatingMap.hx: HxAttributes get() = HxAttributes(this)

@ExperimentalHtmxApi
public inline fun DelegatingMap.hx(block: HxAttributes.() -> Unit) {
    hx.block()
}

@ExperimentalHtmxApi
@HtmlTagMarker
public class HxAttributes(override val map: DelegatingMap) : StringMapDelegate {
    public var get: String? by HxAttributeKeys.Get
    public var post: String? by HxAttributeKeys.Post
    public var pushUrl: String? by HxAttributeKeys.PushUrl
    public var select: String? by HxAttributeKeys.Select
    public var selectOob: String? by HxAttributeKeys.SelectOob
    public var swap: String? by HxAttributeKeys.Swap
    public var swapOob: String? by HxAttributeKeys.SwapOob
    public var target: String? by HxAttributeKeys.Target
    public var trigger: String? by HxAttributeKeys.Trigger
    public var vals: String? by HxAttributeKeys.Vals

    public val on: On
        get() = On(map)

    public fun on(event: String, script: String) {
        map["hx-on:$event"] = script
    }

    @JvmInline
    public value class On(private val attributes: MutableMap<String, String>) {
        public operator fun set(event: String, script: String?) {
            if (script == null) {
                attributes.remove("${HxAttributeKeys.On}:$event")
            } else {
                attributes["${HxAttributeKeys.On}:$event"] = script
            }
        }

        public operator fun get(event: String): String? = attributes["${HxAttributeKeys.On}:$event"]
    }
}
