/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

import io.ktor.client.utils.*
import kotlin.js.Promise

// external fun fetch(input: Request, init: RequestInit? = definedExternally): Promise<Response>

public external fun fetch(input: String, init: RequestInit? = definedExternally): Promise<org.w3c.fetch.Response>

public external interface Request : Body {
    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    public var cache: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    public var credentials: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "audio" | "audioworklet" | "document" | "embed" | "font" | "image" | "manifest" | "object" | "paintworklet" | "report" | "script" | "sharedworker" | "style" | "track" | "video" | "worker" | "xslt" */
    public var destination: JsAny?
        get() = definedExternally
        set(value) = definedExternally
    public var headers: Headers
    public var integrity: String
    public var isHistoryNavigation: Boolean
    public var isReloadNavigation: Boolean
    public var keepalive: Boolean
    public var method: String

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    public var mode: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    public var redirect: JsAny?
        get() = definedExternally
        set(value) = definedExternally
    public var referrer: String

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    public var referrerPolicy: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    public var signal: AbortSignal

    public var url: String

    public fun clone(): Request
}

public external interface Response : Body {
    public var headers: Headers
    public var ok: Boolean
    public var redirected: Boolean
    public var status: Int
    public var statusText: String
    public var trailer: Promise<Headers>

    /* "basic" | "cors" | "default" | "error" | "opaque" | "opaqueredirect" */
    public var type: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    public var url: String
    public fun clone(): Response
}

public external interface Body : JsAny {
    public var body: ReadableStream<Uint8Array>?
        get() = definedExternally
        set(value) = definedExternally
    public var bodyUsed: Boolean
    public fun arrayBuffer(): Promise<ArrayBuffer>
    public fun blob(): Promise<Blob>
    public fun formData(): Promise<FormData>
    public fun json(): Promise<JsAny>
    public fun text(): Promise<JsString>
}

public external interface FormData : JsAny {
    public fun append(name: String, value: String, fileName: String? = definedExternally)
    public fun append(name: String, value: Blob, fileName: String? = definedExternally)
    public fun delete(name: String)

    /* File | String */
    public fun get(name: String): JsAny?
    public fun getAll(name: String): ArrayLike<JsAny?> /* File | String */
    public fun has(name: String): Boolean
    public fun set(name: String, value: String, fileName: String? = definedExternally)
    public fun set(name: String, value: Blob, fileName: String? = definedExternally)
    public fun forEach(
        callbackfn: (value: JsAny? /* File | String */, key: String, parent: FormData) -> Unit,
        thisArg: JsAny? = definedExternally
    )
}

public external interface Blob : JsAny {
    public var size: Int
    public var type: String
    public fun slice(
        start: Int? = definedExternally,
        end: Int? = definedExternally,
        contentType: String? = definedExternally
    ): Blob
}

public external interface ReadableStream<R : JsAny?> : JsAny {
    public var locked: Boolean
    public fun cancel(reason: JsAny? = definedExternally): Promise<JsAny?>
    public fun getReader(options: JsAny?): ReadableStreamBYOBReader
    public fun getReader(): ReadableStreamDefaultReader<R>

//    fun <T> pipeThrough(__0: `T$1`, options: PipeOptions? = definedExternally): ReadableStream<T>

    public fun pipeTo(dest: WritableStream<R>, options: PipeOptions? = definedExternally): Promise<JsAny?>

    /* JsTuple<ReadableStream<R>, ReadableStream<R>> */
    public fun tee(): JsAny?
}

public external interface PipeOptions : JsAny {
    public var preventAbort: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var preventCancel: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var preventClose: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface WritableStream<W : JsAny?> : JsAny {
    public var locked: Boolean
    public fun abort(reason: JsAny? = definedExternally): Promise<JsAny?>
    public fun getWriter(): WritableStreamDefaultWriter<W>
}

public external interface WritableStreamDefaultWriter<W : JsAny?> : JsAny {
    public var closed: Promise<JsAny?>
    public var desiredSize: Int?
        get() = definedExternally
        set(value) = definedExternally
    public var ready: Promise<JsAny?>
    public fun abort(reason: JsAny? = definedExternally): Promise<JsAny?>
    public fun close(): Promise<JsAny?>
    public fun releaseLock()
    public fun write(chunk: W): Promise<JsAny?>
}

public external interface ReadableStreamBYOBReader : JsAny {
    public var closed: Promise<JsAny?>
    public fun cancel(reason: JsAny? = definedExternally): Promise<JsAny?>
    public fun <T : ArrayBufferView> read(view: T): Promise<ReadableStreamReadResult<T>>
    public fun releaseLock()
}

public external interface ReadableStreamDefaultReader<R : JsAny?> : JsAny {
    public var closed: Promise<JsAny?>
    public fun cancel(reason: JsAny? = definedExternally): Promise<JsAny?>
    public fun read(): Promise<ReadableStreamReadResult<R>>
    public fun releaseLock()
}

public external interface ReadableStreamReadResult<T : JsAny?> : JsAny {
    public var done: Boolean
    public var value: T
}

public external interface Headers : JsAny {
    public fun append(name: String, value: String)
    public fun delete(name: String)
    public fun get(name: String): String?
    public fun has(name: String): Boolean
    public fun set(name: String, value: String)
    public fun forEach(
        callbackfn: (value: String, key: String, parent: Headers) -> Unit,
        thisArg: JsAny? = definedExternally
    )
}

public external interface RequestInit : JsAny {
    /* Blob | ArrayBufferView | ArrayBuffer | FormData | URLSearchParams | ReadableStream<Uint8Array> | String */
    public var body: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    public var cache: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    public var credentials: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* Headers | Array<Array<String>> | Record<String, String> */
    public var headers: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    public var integrity: String?
        get() = definedExternally
        set(value) = definedExternally

    public var keepalive: Boolean?
        get() = definedExternally
        set(value) = definedExternally

    public var method: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    public var mode: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    public var redirect: JsAny?
        get() = definedExternally
        set(value) = definedExternally
    public var referrer: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    public var referrerPolicy: JsAny?
        get() = definedExternally
        set(value) = definedExternally

    public var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally

    public var window: JsAny?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface AbortController : JsAny {
    public var signal: AbortSignal
    public fun abort()
}

public external interface AbortSignal : EventTarget {
    public var aborted: Boolean
    public var onabort: ((AbortSignal, ev: Event) -> JsAny)?
        get() = definedExternally
        set(value) = definedExternally

    public fun <K : JsAny> addEventListener(
        type: K,
        listener: (AbortSignal, ev: JsAny) -> JsAny,
        options: Boolean? = definedExternally
    )

    public fun <K : JsAny> addEventListener(
        type: K,
        listener: (AbortSignal, ev: JsAny) -> JsAny,
        options: AddEventListenerOptions? = definedExternally
    )

    override fun addEventListener(type: String, listener: EventListener, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: AddEventListenerOptions?)
    public fun <K : JsAny> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: JsAny) -> JsAny,
        options: Boolean? = definedExternally
    )

    public fun <K : JsAny> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: JsAny) -> JsAny,
        options: EventListenerOptions? = definedExternally
    )

    override fun removeEventListener(type: String, callback: EventListener, options: Boolean?)
    override fun removeEventListener(type: String, callback: EventListener, options: EventListenerOptions?)
    override fun removeEventListener(type: String, callback: EventListenerObject, options: Boolean?)
    override fun removeEventListener(type: String, callback: EventListenerObject, options: EventListenerOptions?)
    public fun <K : JsAny> addEventListener(type: K, listener: (AbortSignal, ev: JsAny) -> JsAny)
    override fun addEventListener(type: String, listener: EventListener)
    override fun addEventListener(type: String, listener: EventListenerObject)
    public fun <K : JsAny> removeEventListener(type: K, listener: (AbortSignal, ev: JsAny) -> JsAny)
    override fun removeEventListener(type: String, callback: EventListener)
    override fun removeEventListener(type: String, callback: EventListenerObject)
}

public external interface EventTarget : JsAny {
    public fun addEventListener(type: String, listener: EventListener, options: Boolean? = definedExternally)
    public fun addEventListener(
        type: String,
        listener: EventListener,
        options: AddEventListenerOptions? = definedExternally
    )

    public fun addEventListener(type: String, listener: EventListenerObject, options: Boolean? = definedExternally)
    public fun addEventListener(
        type: String,
        listener: EventListenerObject,
        options: AddEventListenerOptions? = definedExternally
    )

    public fun dispatchEvent(event: Event): Boolean
    public fun removeEventListener(
        type: String,
        callback: EventListener,
        options: EventListenerOptions? = definedExternally
    )

    public fun removeEventListener(type: String, callback: EventListener, options: Boolean? = definedExternally)
    public fun removeEventListener(
        type: String,
        callback: EventListenerObject,
        options: EventListenerOptions? = definedExternally
    )

    public fun removeEventListener(type: String, callback: EventListenerObject, options: Boolean? = definedExternally)
    public fun addEventListener(type: String, listener: EventListener)
    public fun addEventListener(type: String, listener: EventListenerObject)
    public fun removeEventListener(type: String, callback: EventListener)
    public fun removeEventListener(type: String, callback: EventListenerObject)
}

public external interface EventListener : JsAny

@Suppress("NOTHING_TO_INLINE")
public inline operator fun EventListener.invoke(evt: Event) {
    makeJsCall(this, evt)
}

public external interface AddEventListenerOptions : EventListenerOptions {
    public var once: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var passive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface EventListenerOptions : JsAny {
    public var capture: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface EventListenerObject : JsAny {
    public fun handleEvent(evt: Event)
}

public external interface Event : JsAny {
    public var bubbles: Boolean
    public var cancelBubble: Boolean
    public var cancelable: Boolean
    public var composed: Boolean
    public var currentTarget: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var defaultPrevented: Boolean
    public var eventPhase: Int
    public var isTrusted: Boolean
    public var returnValue: Boolean
    public var srcElement: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var target: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var timeStamp: JsNumber
    public var type: String
    public fun composedPath(): ArrayLike<EventTarget>
    public fun initEvent(type: String, bubbles: Boolean? = definedExternally, cancelable: Boolean? = definedExternally)
    public fun preventDefault()
    public fun stopImmediatePropagation()
    public fun stopPropagation()
    public var AT_TARGET: Int
    public var BUBBLING_PHASE: Int
    public var CAPTURING_PHASE: Int
    public var NONE: Int
}
