/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import platform.linux.*
import platform.posix.*
import threadUtils.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun collectStack(thread: pthread_t): List<String> {
    val size = collect_stack(thread)
    if (size < 0) throw IllegalArgumentException("Thread is stopped")
    val symbols = backtrace_symbols(callstack, size)!!
    return List(stack_size) { symbols[it]!!.toKString() }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun setSignalHandler() {
    set_signal_handler()
}
