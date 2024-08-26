/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.forwardedheaders

internal fun String.isNotHostAddress(): Boolean {
    return if (contains(':')) {
        return true
    } else {
        none { it.isLetter() }
    }
}
