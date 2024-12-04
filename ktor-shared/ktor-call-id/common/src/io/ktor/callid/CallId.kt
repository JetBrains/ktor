/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.callid

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * The default call ID's generator dictionary.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.callid.CALL_ID_DEFAULT_DICTIONARY)
 */
public const val CALL_ID_DEFAULT_DICTIONARY: String = "abcdefghijklmnopqrstuvwxyz0123456789+/=-"

/**
 * A coroutine context element that holds a call ID of the current coroutine.
 *
 * @see withCallId
 */
public class KtorCallIdContextElement(public val callId: String) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = KtorCallIdContextElement

    public companion object : CoroutineContext.Key<KtorCallIdContextElement>
}

/**
 * Adds [callId] to the current coroutine context.
 *
 * @see KtorCallIdContextElement
 */
public suspend fun withCallId(callId: String, block: suspend () -> Unit): Unit =
    withContext(KtorCallIdContextElement(callId)) { block() }
