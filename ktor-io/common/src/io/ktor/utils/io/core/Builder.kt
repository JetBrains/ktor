package io.ktor.utils.io.core

import kotlinx.io.*
import kotlin.contracts.*

/**
 * Build a byte packet in [block] lambda. Creates a temporary builder and releases it in case of failure
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
