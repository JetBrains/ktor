/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*
import kotlin.contracts.*

/**
 * Build a byte packet in [block] lambda. Creates a temporary builder and releases it in case of failure
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.buildPacket)
 */
@OptIn(ExperimentalContracts::class)
public inline fun buildPacket(block: Sink.() -> Unit): Source {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val builder = kotlinx.io.Buffer()
    block(builder)
    return builder
}
