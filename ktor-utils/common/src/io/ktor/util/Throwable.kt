/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.*

/**
 * Root cause of the [Throwable].
 */
@InternalAPI
public val Throwable.rootCause: Throwable?
    get() {
        var rootCause: Throwable? = this
        while (rootCause?.cause != null) {
            rootCause = rootCause.cause
        }
        return rootCause
    }
