/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

public expect interface Closeable {
    public fun close()
}

public inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    var closed = false
    try {
        return block(this)
    } catch (cause: Throwable) {
        closed = true
        try {
            this?.close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
        throw cause
    } finally {
        if (!closed) {
            this?.close()
        }
    }
}
