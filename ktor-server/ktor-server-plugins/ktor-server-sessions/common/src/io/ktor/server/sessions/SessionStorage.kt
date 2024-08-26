/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

/**
 * A storage that provides the ability to [write], [read], and [invalidate] session data.
 *
 * @see [Sessions]
 */
public interface SessionStorage {
    /**
     * Writes a session [value] for [id].
     */
    public suspend fun write(id: String, value: String)

    /**
     * Invalidates a session with the [id] identifier.
     * This method prevents a session [id] from being accessible after this call.
     *
     * @throws NoSuchElementException when a session [id] is not found.
     */
    public suspend fun invalidate(id: String)

    /**
     * Reads a session with the [id] identifier.
     *
     * @throws NoSuchElementException when a session [id] is not found.
     */
    public suspend fun read(id: String): String
}
