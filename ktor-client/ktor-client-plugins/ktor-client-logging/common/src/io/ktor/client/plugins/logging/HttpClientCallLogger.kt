/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import kotlinx.atomicfu.*
import kotlinx.coroutines.*

internal class HttpClientCallLogger(private val logger: Logger) {
    private val requestLog = StringBuilder()
    private val responseLog = StringBuilder()
    private val requestLoggedMonitor = Job()
    private val responseHeaderMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    fun logRequest(message: String) {
        requestLog.appendLine(message.trim())
    }

    fun logResponseHeader(message: String) {
        responseLog.appendLine(message.trim())
        responseHeaderMonitor.complete()
    }

    suspend fun logResponseException(message: String) {
        requestLoggedMonitor.join()
        logger.log(message.trim())
    }

    suspend fun logResponseBody(message: String) {
        responseHeaderMonitor.join()
        responseLog.append(message)
    }

    fun closeRequestLog() {
        if (!requestLogged.compareAndSet(false, true)) return

        try {
            val message = requestLog.trim().toString()
            if (message.isNotEmpty()) {
                logger.log(message)
            }
        } finally {
            requestLoggedMonitor.complete()
        }
    }

    suspend fun closeResponseLog() {
        if (!responseLogged.compareAndSet(false, true)) return

        requestLoggedMonitor.join()
        val message = responseLog.trim().toString()
        if (message.isNotEmpty()) logger.log(message)
    }
}
