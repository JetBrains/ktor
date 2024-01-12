/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.darwin.internal.*
import io.ktor.client.request.*
import io.ktor.util.collections.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

private const val HTTP_REQUESTS_INITIAL_CAPACITY = 32
private const val WS_REQUESTS_INITIAL_CAPACITY = 16

/**
 * Creates an instance of [KtorNSURLSessionDelegate]
 */
@OptIn(UnsafeNumber::class)
public fun KtorNSURLSessionDelegate(): KtorNSURLSessionDelegate {
    return KtorNSURLSessionDelegate(null)
}

/**
 * A delegate for [NSURLSession] that bridges it to Ktor.
 * If users set custom session in [DarwinClientEngineConfig.sessionAndDelegate],
 * they need to register this delegate in their session.
 * This can be done by registering it directly,
 * extending their custom delegate from it
 * or by calling required methods from their custom delegate.
 *
 * For HTTP requests to work property, it's important that users call these functions:
 *   * URLSession:dataTask:didReceiveData:
 *   * URLSession:task:didCompleteWithError:
 *   * URLSession:task:willPerformHTTPRedirection:newRequest:completionHandler:
 *
 * For WebSockets to work, it's important that users call these functions:
 *   * URLSession:webSocketTask:didOpenWithProtocol:
 *   * URLSession:webSocketTask:didCloseWithCode:reason:
 */
@OptIn(UnsafeNumber::class)
public class KtorNSURLSessionDelegate(
    internal val challengeHandler: ChallengeHandler?
) : NSObject(), NSURLSessionDataDelegateProtocol, NSURLSessionWebSocketDelegateProtocol {

    private val taskHandlers: ConcurrentMap<NSURLSessionTask, DarwinTaskHandler> =
        ConcurrentMap(HTTP_REQUESTS_INITIAL_CAPACITY)

    private val webSocketSessions: ConcurrentMap<NSURLSessionWebSocketTask, DarwinWebsocketSession> =
        ConcurrentMap(WS_REQUESTS_INITIAL_CAPACITY)

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        val taskHandler = taskHandlers[dataTask] ?: return
        taskHandler.receiveData(dataTask, didReceiveData)
    }

    override fun URLSession(session: NSURLSession, taskIsWaitingForConnectivity: NSURLSessionTask) {
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        taskHandlers[task]?.let {
            it.complete(task, didCompleteWithError)
            taskHandlers.remove(task)
        }

        webSocketSessions[task]?.let {
            it.didComplete(didCompleteWithError)
        }
    }

    override fun URLSession(
        session: NSURLSession,
        webSocketTask: NSURLSessionWebSocketTask,
        didOpenWithProtocol: String?
    ) {
        val wsSession = webSocketSessions[webSocketTask] ?: return
        wsSession.didOpen()
    }

    override fun URLSession(
        session: NSURLSession,
        webSocketTask: NSURLSessionWebSocketTask,
        didCloseWithCode: NSURLSessionWebSocketCloseCode,
        reason: NSData?
    ) {
        val wsSession = webSocketSessions[webSocketTask] ?: return
        wsSession.didClose(didCloseWithCode, reason, webSocketTask)
    }

    internal fun read(
        task: NSURLSessionWebSocketTask,
        callContext: CoroutineContext
    ): CompletableDeferred<HttpResponseData> {
        val taskHandler = DarwinWebsocketSession(callContext, task)
        webSocketSessions[task] = taskHandler
        return taskHandler.response
    }

    internal fun read(
        request: HttpRequestData,
        callContext: CoroutineContext,
        task: NSURLSessionTask
    ): CompletableDeferred<HttpResponseData> {
        val taskHandler = DarwinTaskHandler(request, callContext)
        taskHandlers.put(task, taskHandler)
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
