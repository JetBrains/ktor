/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Print [Job] children tree.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.printDebugTree)
 */
public fun Job.printDebugTree(offset: Int = 0) {
    println(" ".repeat(offset) + this)

    children.forEach {
        it.printDebugTree(offset + 2)
    }

    if (offset == 0) println()
}

/**
 * Supervisor with empty coroutine exception handler ignoring all exceptions.
 */
@Suppress("FunctionName")
public fun SilentSupervisor(parent: Job? = null): CoroutineContext =
    SupervisorJob(parent) + CoroutineExceptionHandler { _, _ -> }
