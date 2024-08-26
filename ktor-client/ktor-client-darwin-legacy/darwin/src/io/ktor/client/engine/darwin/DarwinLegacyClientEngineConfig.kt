/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * A challenge handler type for [NSURLSession].
 */
@OptIn(UnsafeNumber::class)
public typealias ChallengeHandler = (
    session: NSURLSession,
    task: NSURLSessionTask,
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) -> Unit

/**
 * A configuration for the [DarwinLegacy] client engine.
 */
public class DarwinLegacyClientEngineConfig : HttpClientEngineConfig() {
    /**
     * A request configuration.
     */
    public var requestConfig: NSMutableURLRequest.() -> Unit = {}
        @Deprecated(
            "[requestConfig] property is deprecated. Consider using [configureRequest] instead",
            replaceWith = ReplaceWith("this.configureRequest(value)"),
            level = DeprecationLevel.ERROR
        )
        set

    /**
     * A session configuration.
     */
    public var sessionConfig: NSURLSessionConfiguration.() -> Unit = {}
        @Deprecated(
            "[sessionConfig] property is deprecated. Consider using [configureSession] instead",
            replaceWith = ReplaceWith("this.configureSession(value)"),
            level = DeprecationLevel.ERROR
        )
        set

    /**
     * Handles the challenge of HTTP responses [NSURLSession].
     */
    @OptIn(UnsafeNumber::class)
    public var challengeHandler: ChallengeHandler? = null
        private set

    /**
     * Specifies a session to use for making HTTP requests.
     */
    public var preconfiguredSession: NSURLSession? = null
        private set

    /**
     * Specifies a session to use for making HTTP requests.
     */
    internal var sessionAndDelegate: Pair<NSURLSession, KtorLegacyNSURLSessionDelegate>? = null

    /**
     * Appends a block with the [NSMutableURLRequest] configuration to [requestConfig].
     */
    public fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        @Suppress("DEPRECATION_ERROR")
        requestConfig = {
            old()
            block()
        }
    }

    /**
     * Appends a block with the [NSURLSessionConfiguration] configuration to [sessionConfig].
     */
    public fun configureSession(block: NSURLSessionConfiguration.() -> Unit) {
        val old = sessionConfig

        @Suppress("DEPRECATION_ERROR")
        sessionConfig = {
            old()
            block()
        }
    }

    /**
     * Set a [session] to be used to make HTTP requests, [null] to create default session.
     * If the preconfigured session is set, [configureSession] block will be ignored.
     */
    @Deprecated("Please use method with delegate parameter", level = DeprecationLevel.ERROR)
    public fun usePreconfiguredSession(session: NSURLSession?) {
        preconfiguredSession = session
    }

    /**
     * Set a [session] to be used to make HTTP requests.
     * If the preconfigured session is set, [configureSession] and [handleChallenge] blocks will be ignored.
     *
     * The [session] must be created with [KtorLegacyNSURLSessionDelegate] as a delegate.
     * @see [KtorLegacyNSURLSessionDelegate] for details.
     */
    public fun usePreconfiguredSession(session: NSURLSession, delegate: KtorLegacyNSURLSessionDelegate) {
        sessionAndDelegate = session to delegate
    }

    /**
     * Sets the [block] as an HTTP request challenge handler replacing the old one.
     */
    @OptIn(UnsafeNumber::class)
    public fun handleChallenge(block: ChallengeHandler) {
        challengeHandler = block
    }
}
