/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.*

/**
 * [SessionTracker] that transfers a Session Id generated by a [sessionIdProvider] in HTTP Headers/Cookies.
 * It uses a [storage] and a [serializer] to store/load serialized/deserialized session content of a specific [type].
 *
 * @property type is a session instance type
 * @property serializer session serializer
 * @property storage session storage to store session
 * @property sessionIdProvider is a function that generates session IDs
 */
public class SessionTrackerById<S : Any>(
    public val type: KClass<S>,
    public val serializer: SessionSerializer<S>,
    public val storage: SessionStorage,
    public val sessionIdProvider: () -> String
) : SessionTracker<S> {
    private val SessionIdKey = AttributeKey<String>("SessionId")

    override suspend fun load(call: ApplicationCall, transport: String?): S? {
        val sessionId = transport ?: return null

        call.attributes.put(SessionIdKey, sessionId)
        try {
            return storage.read(sessionId) { channel ->
                val text = channel.readUTF8Line()
                    ?: throw IllegalStateException("Failed to read stored session from $channel")
                serializer.deserialize(text)
            }
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug(
                "Failed to lookup session: ${notFound.message ?: notFound.toString()}. " +
                    "The session id is wrong or outdated."
            )
        }

        // we remove the wrong session identifier if no related session found
        call.attributes.put(SessionIdKey, sessionId)

        return null
    }

    override suspend fun store(call: ApplicationCall, value: S): String {
        val sessionId = call.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(value)
        storage.write(sessionId) { channel ->
            channel.writeStringUtf8(serialized)
            channel.close()
        }
        return sessionId
    }

    override suspend fun clear(call: ApplicationCall) {
        val sessionId = call.attributes.takeOrNull(SessionIdKey)
        if (sessionId != null) {
            storage.invalidate(sessionId)
        }
    }

    override fun validate(value: S) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }

    override fun toString(): String {
        return "SessionTrackerById: $storage"
    }
}
