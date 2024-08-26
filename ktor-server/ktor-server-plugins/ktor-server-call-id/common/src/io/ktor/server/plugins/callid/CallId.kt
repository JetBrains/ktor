/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.callid

import io.ktor.callid.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.internal.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.random.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.callid.CallId")

/**
 * A function that retrieves or generates call id using provided call
 */
public typealias CallIdProvider = (call: ApplicationCall) -> String?

/**
 * A function that verifies retrieved or generated call id. Should return `true` for a valid call id.
 * Also it could throw a [RejectedCallIdException] to reject an [ApplicationCall] otherwise an illegal call id
 * will be ignored or replaced with generated one.
 */
public typealias CallIdVerifier = (String) -> Boolean

/**
 * An exception that could be thrown to reject a call due to illegal call id
 * @param illegalCallId that caused rejection
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class RejectedCallIdException(
    public val illegalCallId: String
) : IllegalArgumentException(), CopyableThrowable<RejectedCallIdException> {
    override fun createCopy(): RejectedCallIdException = RejectedCallIdException(illegalCallId).also {
        it.initCauseBridge(this)
    }
}

/**
 * A configuration for the [CallId] plugin.
 */
@KtorDsl
public class CallIdConfig {
    internal val retrievers = ArrayList<CallIdProvider>()
    internal val generators = ArrayList<CallIdProvider>()
    internal val responseInterceptors = ArrayList<(call: ApplicationCall, CallId: String) -> Unit>()

    internal var verifier: CallIdVerifier = { false }

    init {
        verify(CALL_ID_DEFAULT_DICTIONARY)
    }

    /**
     * Allows you to retrieve a call ID from [ApplicationCall].
     * Returns `null` if no call ID is found in a request.
     *
     * @see verify
     */
    public fun retrieve(block: CallIdProvider) {
        retrievers.add(block)
    }

    /**
     * Allows you to generate a call ID if an incoming request doesn't include it.
     * Generates `null` if it is impossible to generate a call ID for some reason.
     *
     * @see verify
     */
    public fun generate(block: CallIdProvider) {
        generators.add(block)
    }

    /**
     * Verifies a retrieved or generated call ID.
     * The code below verifies that a call ID is not an empty string:
     * ```kotlin
     * verify { callId: String ->
     *     callId.isNotEmpty()
     * }
     * ```
     *
     * Note that by default all retrieved/generated call IDs are verified using a default dictionary, which looks as follows:
     *
     * ```kotlin
     * CALL_ID_DEFAULT_DICTIONARY: String = "abcdefghijklmnopqrstuvwxyz0123456789+/=-"
     * ```
     *
     * @see [CallIdVerifier]
     */
    public fun verify(predicate: CallIdVerifier) {
        verifier = predicate
    }

    /**
     * Verifies a retrieved or generated call ID against the specified [dictionary].
     * Rejects an [ApplicationCall] if [reject] is `true`; otherwise, an illegal call ID is ignored.
     *
     * Note that by default all retrieved/generated call IDs are verified using a default dictionary, which looks as follows:
     *
     * ```kotlin
     * CALL_ID_DEFAULT_DICTIONARY: String = "abcdefghijklmnopqrstuvwxyz0123456789+/=-"
     * ```
     */
    public fun verify(dictionary: String, reject: Boolean = false) {
        val dictionarySet = dictionary.toSet()
        verify { callId ->
            if (!verifyCallIdAgainstDictionary(callId, dictionarySet)) {
                if (reject) throw RejectedCallIdException(callId)
                false
            } else {
                true
            }
        }
    }

    /**
     * Allows you to reply with a retrieved or generated call ID by modifying an [ApplicationCall].
     *
     * @see [replyToHeader]
     */
    public fun reply(block: (call: ApplicationCall, CallId: String) -> Unit) {
        responseInterceptors.add(block)
    }

    /**
     * Allows you to retrieve a call ID and send it in the same header.
     *
     * @see [retrieveFromHeader]
     * @see [replyToHeader]
     */
    public fun header(headerName: String) {
        retrieveFromHeader(headerName)
        replyToHeader(headerName)
    }

    /**
     * Retrieves a call ID from a specified request header named [headerName].
     *
     * @see [replyToHeader]
     */
    public fun retrieveFromHeader(headerName: String) {
        retrieve { it.request.headers[headerName] }
    }

    /**
     * Replies with a call ID using a specified header named [headerName].
     *
     * @see [retrieveFromHeader]
     */
    public fun replyToHeader(headerName: String) {
        reply { call, CallId ->
            call.response.header(headerName, CallId)
        }
    }
}

internal val CallIdKey: AttributeKey<String> = AttributeKey("ExtractedCallId")

internal object CallIdSetup : Hook<suspend PipelineContext<Unit, PipelineCall>.(ApplicationCall) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend PipelineContext<Unit, PipelineCall>.(ApplicationCall) -> Unit
    ) {
        pipeline.intercept(ApplicationCallPipeline.Setup) {
            handler(call)
        }
    }
}

/**
 * A plugin that allows you to trace client requests end-to-end by using unique request IDs or call IDs.
 * Typically, working with a call ID in Ktor might look as follows:
 * 1. First, you need to obtain a call ID for a specific request in one of the following ways:
 *    - A reverse proxy (such as Nginx) or cloud provider (such as Heroku) might add a call ID in a specific header,
 *    for example, `X-Request-Id`. In this case, Ktor allows you to retrieve a call ID.
 *    - Otherwise, if a request comes without a call ID, you can generate it on the Ktor server.
 * 2. Next, Ktor verifies a retrieved/generated call ID using a predefined dictionary.
 * You can also provide your own condition to verify a call ID.
 * 3. The plugin will add a call ID to the coroutine context for this call.
 * You can access it by using `coroutineContext[KtorCallIdContextElement].callId`
 * 4. Finally, you can send a call ID to the client in a specific header, for example, `X-Request-Id`.
 *
 * You can learn more from [CallId](https://ktor.io/docs/call-id.html).
 */
public val CallId: RouteScopedPlugin<CallIdConfig> = createRouteScopedPlugin(
    "CallId",
    ::CallIdConfig
) {
    val providers = (pluginConfig.retrievers + pluginConfig.generators).toTypedArray()
    val repliers = pluginConfig.responseInterceptors.toTypedArray()
    val verifier = pluginConfig.verifier

    on(CallIdSetup) { call ->
        for (provider in providers) {
            val callId = provider(call) ?: continue
            if (!verifier(callId)) continue // could throw a RejectedCallIdException

            call.attributes.put(CallIdKey, callId)
            LOGGER.trace("Setting id for a call ${call.request.uri} to $callId")

            repliers.forEach { replier ->
                replier(call, callId)
            }

            withCallId(callId) {
                proceed()
            }
            break
        }
    }

    on(CallFailed) { call, cause ->
        if (cause !is RejectedCallIdException) return@on
        LOGGER.warn(
            "Illegal call id retrieved or generated that is rejected by call id verifier: (url-encoded) " +
                cause.illegalCallId.encodeURLParameter()
        )
        call.respond(HttpStatusCode.BadRequest)
    }
}

/**
 * Gets a call ID retrieved or generated by the [CallId] plugin.
 * Returns `null` if there is no call ID is provided and no generators are configured.
 */
public val ApplicationCall.callId: String? get() = attributes.getOrNull(CallIdKey)

private fun verifyCallIdAgainstDictionary(callId: String, dictionarySet: Set<Char>): Boolean {
    for (element in callId) {
        if (!dictionarySet.contains(element)) {
            return false
        }
    }

    return true
}

/**
 * Generates a fixed [length] call ID using the specified [dictionary].
 * Note that this function generates pseudo-random identifiers via regular [java.util.Random]
 * and should not be considered as cryptographically secure.
 * Also note that you need to use the same dictionary for [CallIdVerifier], otherwise a generated call ID could be
 * discarded or may lead to complete call rejection.
 *
 * @see [CallIdConfig.verify]
 *
 * @param length of call IDs to be generated, should be positive
 * @param dictionary to be used to generate IDs, shouldn't be empty and shouldn't contain duplicates
 */
public fun CallIdConfig.generate(length: Int = 64, dictionary: String = CALL_ID_DEFAULT_DICTIONARY) {
    require(length >= 1) { "Call id should be at least one characters length: $length" }
    require(dictionary.length > 1) { "Dictionary should consist of several different characters" }

    val dictionaryCharacters = dictionary.toCharArray().distinct().toCharArray()

    require(dictionaryCharacters.size == dictionary.length) {
        "Dictionary should not contain duplicates, found: ${dictionary.duplicates()}"
    }

    generate { Random.nextString(length, dictionaryCharacters) }
}

private fun String.duplicates() = toCharArray().groupBy { it }.filterValues { it.size > 1 }.keys.sorted()

private fun Random.nextString(length: Int, dictionary: CharArray): String {
    val chars = CharArray(length)
    val dictionarySize = dictionary.size

    for (index in 0 until length) {
        chars[index] = dictionary[nextInt(dictionarySize)]
    }

    return chars.concatToString()
}
