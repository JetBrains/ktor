/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Gets a current session or fails if the [Sessions] plugin is not installed.
 * @throws MissingApplicationPluginException
 */
public val ApplicationCall.sessions: CurrentSession
    get() = attributes.getOrNull(SessionDataKey) ?: reportMissingSession()

/**
 * A container for all session instances.
 */
public interface CurrentSession {
    /**
     * Sets a new session instance with [name].
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun set(name: String, value: Any?)

    /**
     * Gets a session instance for [name]
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun get(name: String): Any?

    /**
     * Clears a session instance for [name].
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun clear(name: String)

    /**
     * Finds a session name for the specified [type] or fails if it's not found.
     * @throws IllegalStateException if no session provider registered for [type]
     */
    public fun findName(type: KClass<*>): String
}

/**
 * Sets a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.set(value: T?): Unit = set(value, T::class)

/**
 * Sets a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public fun <T : Any> CurrentSession.set(value: T?, klass: KClass<T>): Unit = set(findName(klass), value)

/**
 * Gets a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.get(): T? = get(T::class)

/**
 * Gets a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> CurrentSession.get(klass: KClass<T>): T? = get(findName(klass)) as T?

/**
 * Clears a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.clear(): Unit = clear(T::class)

/**
 * Clears a session instance with the type [T].
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public fun <T : Any> CurrentSession.clear(klass: KClass<T>): Unit = clear(findName(klass))

/**
 * Gets or generates a new session instance using [generator] with the type [T] (or [name] if specified)
 * @throws IllegalStateException if no session provider is registered for the type [T] (or [name] if specified)
 */
public inline fun <reified T : Any> CurrentSession.getOrSet(name: String = findName(T::class), generator: () -> T): T {
    val result = get<T>()

    if (result != null) {
        return result
    }

    return generator().apply {
        set(name, this)
    }
}

internal data class SessionData(
    val providerData: Map<String, SessionProviderData<*>>
) : CurrentSession {

    private var committed = false

    internal fun commit() {
        committed = true
    }

    override fun findName(type: KClass<*>): String {
        val entry = providerData.entries.firstOrNull { it.value.provider.type == type }
            ?: throw IllegalArgumentException("Session data for type `$type` was not registered")
        return entry.value.provider.name
    }

    override fun set(name: String, value: Any?) {
        if (committed) {
            throw TooLateSessionSetException()
        }
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        setTyped(providerData, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> setTyped(data: SessionProviderData<S>, value: Any?) {
        if (value != null) {
            data.provider.tracker.validate(value as S)
        }
        data.newValue = value as S
    }

    override fun get(name: String): Any? {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        return providerData.newValue ?: providerData.oldValue
    }

    override fun clear(name: String) {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        providerData.oldValue = null
        providerData.newValue = null
    }
}

internal suspend fun <S : Any> SessionProvider<S>.receiveSessionData(call: ApplicationCall): SessionProviderData<S> {
    val receivedValue = transport.receive(call)
    val unwrapped = tracker.load(call, receivedValue)
    val incoming = receivedValue != null
    return SessionProviderData(unwrapped, null, incoming, this)
}

internal suspend fun <S : Any> SessionProviderData<S>.sendSessionData(call: ApplicationCall) {
    val oldValue = oldValue
    val newValue = newValue
    when {
        newValue != null -> {
            val wrapped = provider.tracker.store(call, newValue)
            provider.transport.send(call, wrapped)
        }

        incoming && oldValue == null -> {
            /* Deleted session should be cleared off */
            provider.transport.clear(call)
            provider.tracker.clear(call)
        }
    }
}

internal data class SessionProviderData<S : Any>(
    var oldValue: S?,
    var newValue: S?,
    val incoming: Boolean,
    val provider: SessionProvider<S>
)

internal val SessionDataKey = AttributeKey<SessionData>("SessionKey")

private fun ApplicationCall.reportMissingSession(): Nothing {
    application.plugin(Sessions) // ensure the plugin is installed
    throw SessionNotYetConfiguredException()
}

/**
 * Thrown when an HTTP response has already been sent but an attempt to modify the session is made.
 */
public class TooLateSessionSetException :
    IllegalStateException("It's too late to set session: response most likely already has been sent")

/**
 * Thrown when a session is asked too early before the [Sessions] plugin had chance to configure it.
 * For example, in a phase before [ApplicationCallPipeline.Plugins] or in a plugin installed before [Sessions] into
 * the same phase.
 */
public class SessionNotYetConfiguredException :
    IllegalStateException("Sessions are not yet ready: you are asking it to early before the Sessions plugin.")
