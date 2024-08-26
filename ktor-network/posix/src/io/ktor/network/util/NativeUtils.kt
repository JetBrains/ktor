/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.utils.io.errors.*

internal inline fun Int.check(
    block: (Int) -> Boolean = { it >= 0 }
): Int {
    if (!block(this)) {
        throw PosixException.forSocketError()
    }

    return this
}
