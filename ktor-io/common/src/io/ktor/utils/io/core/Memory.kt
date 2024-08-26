/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

@Deprecated("ByteArray instead", ReplaceWith("ByteArray"))
public typealias Memory = ByteArray

public fun <T> withMemory(size: Int, block: (ByteArray) -> T): T {
    return block(ByteArray(size))
}

public fun ByteArray.storeIntAt(index: Int, value: Int) {
    this[index] = (value shr 24).toByte()
    this[index + 1] = (value shr 16).toByte()
    this[index + 2] = (value shr 8).toByte()
    this[index + 3] = value.toByte()
}
