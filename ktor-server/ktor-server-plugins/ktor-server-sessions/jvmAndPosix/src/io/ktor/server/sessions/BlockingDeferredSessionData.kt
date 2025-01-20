/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * An implementation of [StatefulSession] that lazily references session providers to
 * avoid unnecessary calls to session storage.
 * All access to the deferred providers is done through blocking calls.
 */
internal class BlockingDeferredSessionData(
    val callContext: CoroutineContext,
    val providerData: Map<String, Deferred<SessionProviderData<*>>>,
) : StatefulSession {

    private var committed = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun sendSessionData(call: ApplicationCall, onEach: (String) -> Unit) {
        for (deferredProvider in providerData.values) {
            // skip non-completed providers because they were not modified
            if (!deferredProvider.isCompleted) continue
            val data = deferredProvider.getCompleted()
            onEach(data.provider.name)
            data.sendSessionData(call)
        }
        committed = true
    }

    override fun findName(type: KClass<*>): String {
        val entry = providerData.values.map {
            it.awaitBlocking()
        }.firstOrNull {
            it.provider.type == type
        } ?: throw IllegalArgumentException("Session data for type `$type` was not registered")

        return entry.provider.name
    }

    override fun set(name: String, value: Any?) {
        if (committed) {
            throw TooLateSessionSetException()
        }
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        setTyped(providerData.awaitBlocking(), value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> setTyped(data: SessionProviderData<S>, value: Any?) {
        if (value != null) {
            data.provider.tracker.validate(value as S)
        }
        data.newValue = value as S
    }

    override fun get(name: String): Any? {
        val providerDataDeferred =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        val providerData = providerDataDeferred.awaitBlocking()
        return providerData.newValue ?: providerData.oldValue
    }

    override fun clear(name: String) {
        val providerDataDeferred =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        val providerData = providerDataDeferred.awaitBlocking()
        providerData.oldValue = null
        providerData.newValue = null
    }

    private fun Deferred<SessionProviderData<*>>.awaitBlocking() =
        runBlocking(callContext) { await() }
}
