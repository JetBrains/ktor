/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import java.io.*

/**
 * Creates a storage that serializes a session's data to a file under the [rootDir] directory.
 *
 * @see [Sessions]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.sessions.directorySessionStorage)
 */
public fun directorySessionStorage(rootDir: File, cached: Boolean = true): SessionStorage = when (cached) {
    true -> CacheStorage(DirectoryStorage(rootDir), 60000)
    false -> DirectoryStorage(rootDir)
}

internal class DirectoryStorage(private val dir: File) : SessionStorage, Closeable {
    init {
        dir.mkdirsOrFail()
    }

    override fun close() {
    }

    override suspend fun write(id: String, value: String) {
        requireId(id)
        val file = fileOf(id)

        file.parentFile?.mkdirsOrFail()
        file.writeText(value)
    }

    override suspend fun read(id: String): String {
        requireId(id)
        try {
            val file = fileOf(id)

            file.parentFile?.mkdirsOrFail()
            return file.readText().takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Failed to read stored session from $file")
        } catch (notFound: FileNotFoundException) {
            throw NoSuchElementException("No session data found for id $id")
        }
    }

    override suspend fun invalidate(id: String) {
        requireId(id)
        try {
            val file = fileOf(id)
            file.delete()
            file.parentFile?.deleteParentsWhileEmpty(dir)
        } catch (notFound: FileNotFoundException) {
            throw NoSuchElementException("No session data found for id $id")
        }
    }

    private fun fileOf(id: String) = File(dir, split(id).joinToString(File.separator, postfix = ".dat"))
    private fun split(id: String) = id.windowedSequence(size = 2, step = 2, partialWindows = true)

    private fun requireId(id: String) {
        if (id.isEmpty()) {
            throw IllegalArgumentException("Session id is empty")
        }
        if (id.indexOfAny(listOf("..", "/", "\\", "!", "?", ">", "<", "\u0000")) != -1) {
            throw IllegalArgumentException("Bad session id $id")
        }
    }
}

private fun File.mkdirsOrFail() {
    if (!this.mkdirs() && !this.exists()) {
        throw IOException("Couldn't create directory $this")
    }
    if (!this.isDirectory) {
        throw IOException("Path is not a directory: $this")
    }
}

private tailrec fun File.deleteParentsWhileEmpty(mostTop: File) {
    if (this != mostTop && isDirectory && exists() && list().isNullOrEmpty()) {
        if (!delete() && exists()) {
            throw IOException("Failed to delete dir $this")
        }

        parentFile.deleteParentsWhileEmpty(mostTop)
    }
}
