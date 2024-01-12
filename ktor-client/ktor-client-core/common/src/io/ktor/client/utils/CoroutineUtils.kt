/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Creates [CoroutineDispatcher] for client with fixed [threadCount] and specified [dispatcherName].
 */
@InternalAPI
public expect fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String = "ktor-client-dispatcher"
): CoroutineDispatcher
