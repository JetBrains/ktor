package io.ktor.server.htmx

import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.jvm.JvmInline

@ExperimentalHtmxApi
public val RoutingRequest.hx: HXRequestHeaders get() = HXRequestHeaders(headers)

@ExperimentalHtmxApi
public val RoutingResponse.hx: HXResponseHeaders get() = HXResponseHeaders(headers)

public val RoutingRequest.isHtmx: Boolean get() = headers[HxRequestHeaders.Request] == "true"

@ExperimentalHtmxApi
@JvmInline
public value class HXRequestHeaders(private val headers: Headers) {

    /** Indicates that the request is via an element using hx-boost */
    public val isBoosted: Boolean get() = headers[HxRequestHeaders.Boosted]?.toBoolean() == true

    /** "true" if the request is for history restoration after a miss in the local history cache */
    public val isHistoryRestore: Boolean get() = headers[HxRequestHeaders.HistoryRestoreRequest]?.toBoolean() == true

    /** The current URL of the browser */
    public val currentUrl: Url? get() = headers[HxRequestHeaders.CurrentURL]?.let { Url(it) }

    /** The user response to an hx-prompt */
    public val prompt: String? get() = headers[HxRequestHeaders.Prompt]

    /** The id of the target element if it exists */
    public val targetId: String? get() = headers[HxRequestHeaders.Target]

    /** The id of the triggered element if it exists */
    public val triggerId: String? get() = headers[HxRequestHeaders.Trigger]

    /** The name of the triggered element if it exists */
    public val triggerName: String? get() = headers[HxRequestHeaders.TriggerName]
}

@ExperimentalHtmxApi
public class HXResponseHeaders(private val headers: ResponseHeaders) : StringMap {

    public var location: String? by HxResponseHeaders.Location
    public var pushUrl: String? by HxResponseHeaders.PushUrl
    public var redirect: String? by HxResponseHeaders.Redirect
    public var refresh: String? by HxResponseHeaders.Refresh // TODO boolean
    public val replaceUrl: String? by HxResponseHeaders.ReplaceUrl

    override fun set(key: String, value: String): Unit =
        headers.append(key, value)

    override fun get(key: String): String? =
        headers[key]

    override fun remove(key: String): String? =
        throw IllegalStateException("Not implemented")
}
