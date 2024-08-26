/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

public typealias Input = kotlinx.io.Source

public val Input.endOfInput: Boolean
    get() = exhausted()

public fun Input.readAvailable(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
    val result = readAtMostTo(buffer, offset, offset + length)
    return if (result == -1) 0 else result
}
