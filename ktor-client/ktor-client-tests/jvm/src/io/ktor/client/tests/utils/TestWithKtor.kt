/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.ktor.junit.coroutines.*
import io.ktor.server.engine.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

@CoroutinesTimeout(5 * 60 * 1000)
abstract class TestWithKtor {
    protected val serverPort: Int = ServerSocket(0).use { it.localPort }
    protected val testUrl: String = "http://localhost:$serverPort"

    abstract val server: EmbeddedServer<*, *>

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    @BeforeEach
    fun startServer() {
        var attempt = 0

        do {
            attempt++
            try {
                server.start(wait = false)
                break
            } catch (cause: Throwable) {
                if (attempt >= 10) throw cause
                Thread.sleep(250L * attempt)
            }
        } while (true)

        ensureServerRunning()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
    }

    private fun ensureServerRunning() {
        do {
            try {
                Socket("localhost", serverPort).close()
                break
            } catch (_: Throwable) {
                Thread.sleep(100)
            }
        } while (true)
    }
}
