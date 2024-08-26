/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.darwin.internal.legacy.*
import io.ktor.client.request.*
import io.ktor.util.collections.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

/**
 * Creates an instance of [KtorLegacyNSURLSessionDelegate]
 */
@OptIn(UnsafeNumber::class)
public fun KtorLegacyNSURLSessionDelegate(): KtorLegacyNSURLSessionDelegate {
    return KtorLegacyNSURLSessionDelegate(null)
}

/**
 * A delegate for [NSURLSession] that bridges it to Ktor.
 * If users set custom session in [DarwinLegacyClientEngineConfig.sessionAndDelegate],
 * they need to register this delegate in their session.
 * This can be done by registering it directly,
 * extending their custom delegate from it
 * or by calling required methods from their custom delegate.
 *
 * For HTTP requests to work property, it's important that users call these functions:
 *   * URLSession:dataTask:didReceiveData:
 *   * URLSession:task:didCompleteWithError:
 *   * URLSession:task:willPerformHTTPRedirection:newRequest:completionHandler:
 */
@OptIn(UnsafeNumber::class)
public class KtorLegacyNSURLSessionDelegate(
    internal val challengeHandler: ChallengeHandler?
) : NSObject(), NSURLSessionDataDelegateProtocol {

    internal val taskHandlers: ConcurrentMap<NSURLSessionTask, DarwinLegacyTaskHandler> =
        ConcurrentMap(initialCapacity = 32)

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        val taskHandler = taskHandlers[dataTask] ?: return
        taskHandler.receiveData(dataTask, didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        val taskHandler = taskHandlers[task] ?: return
        taskHandler.complete(task, didCompleteWithError)
        taskHandlers.remove(task)
    }

    internal fun read(
        request: HttpRequestData,
        callContext: CoroutineContext,
        task: NSURLSessionTask
    ): CompletableDeferred<HttpResponseData> {
        val taskHandler = DarwinLegacyTaskHandler(request, callContext)
        taskHandlers[task] = taskHandler
        return taskHandler.response
    }

    /**
     * Disable embedded redirects.
     */
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        completionHandler(null)
    }

    /**
     * Handle challenge.
     */
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        val handler = challengeHandler
        if (handler != null) {
            handler(session, task, didReceiveChallenge, completionHandler)
        } else {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, didReceiveChallenge.proposedCredential)
        }
    }
}
