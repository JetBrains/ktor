/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.browser.*
import io.ktor.client.engine.js.node.*
import io.ktor.client.fetch.*
import io.ktor.client.utils.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.js.Promise

internal suspend fun commonFetch(
    input: String,
    init: RequestInit,
    config: JsClientEngineConfig,
): org.w3c.fetch.Response = suspendCancellableCoroutine { continuation ->
    val promise: Promise<org.w3c.fetch.Response> = when {
        PlatformUtils.IS_BROWSER -> fetch(input, init)
        else -> {
            val nodeFetch = makeRequire<JsAny>("node-fetch")
            makeJsCall<Promise<org.w3c.fetch.Response>>(nodeFetch, input.toJsString(), init, config.nodeOptions)
        }
    }

    promise.then(
        onFulfilled = { x: JsAny ->
            continuation.resumeWith(Result.success(x.unsafeCast()))
            null
        },
        onRejected = { it: JsAny ->
            continuation.resumeWith(Result.failure(Error("Fail to fetch", JsError(it))))
            null
        }
    )
}

private fun abortControllerCtorBrowser(): AbortController =
    js("AbortController")

internal fun AbortController(): AbortController {
    val ctor = when {
        PlatformUtils.IS_BROWSER -> abortControllerCtorBrowser()
        else -> makeRequire("abort-controller")
    }
    return makeJsNew(ctor)
}

internal fun CoroutineScope.readBody(
    response: org.w3c.fetch.Response
): ByteReadChannel = when {
    PlatformUtils.IS_NODE -> readBodyNode(response)
    else -> readBodyBrowser(response)
}
