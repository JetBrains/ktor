/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.util.collections.*

/**
 * A storage that keeps session data in memory.
 *
 * Note that [SessionStorageMemory] is intended for development only.
 *
 * @see [Sessions]
 */
public class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentMap<String, String>()

    override suspend fun write(id: String, value: String) {
        sessions[id] = value
    }

    override suspend fun read(id: String): String =
        sessions[id] ?: throw NoSuchElementException("Session $id not found")

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
