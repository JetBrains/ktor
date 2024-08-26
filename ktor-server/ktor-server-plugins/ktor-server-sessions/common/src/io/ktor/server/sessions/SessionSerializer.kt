/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.sessions.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

/**
 * Serializes a session data from and to [String].
 *
 * @see [Sessions]
 */
public interface SessionSerializer<T> {
    /**
     * Serializes a complex arbitrary object into a [String].
     */
    public fun serialize(session: T): String

    /**
     * Deserializes a complex arbitrary object from a [String].
     */
    public fun deserialize(text: String): T
}

/**
 * Creates the default [SessionSerializer] for the type [T].
 */
public inline fun <reified T : Any> defaultSessionSerializer(): SessionSerializer<T> =
    defaultSessionSerializer(typeOf<T>())

/**
 * Creates the default [SessionSerializer] by [typeInfo].
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> defaultSessionSerializer(typeInfo: KType): SessionSerializer<T> =
    KotlinxSessionSerializer(serializer(typeInfo) as KSerializer<T>, Json)
